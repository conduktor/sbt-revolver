/*
 * Copyright (C) 2009-2012 Johannes Rudolph and Mathias Doenitz
 * Copyright (C) 2024 Conduktor
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.conduktor.revolver

import sbt.Keys._
import sbt.{Fork, ForkOptions, LoggedOutput, Logger, Path, ProjectRef, State, complete}

import java.io.File
import scala.sys.process.Process

object Actions {
  import Utilities._

  def restartApp(
      streams: TaskStreams,
      logTag: String,
      project: ProjectRef,
      option: ForkOptions,
      mainClass: Option[String],
      cp: Classpath,
      args: Seq[String],
      startConfig: ExtraCmdLineOptions
  ): AppProcess = {
    stopAppWithStreams(streams, project)
    startApp(streams, logTag, project, option, mainClass, cp, args, startConfig)
  }

  def startApp(
      streams: TaskStreams,
      logTag: String,
      project: ProjectRef,
      options: ForkOptions,
      mainClass: Option[String],
      cp: Classpath,
      args: Seq[String],
      startConfig: ExtraCmdLineOptions
  ): AppProcess = {
    assert(!revolverState.getProcess(project).exists(_.isRunning))

    val theMainClass = mainClass.getOrElse(sys.error("No main class detected!"))
    val color = updateStateAndGet(_.takeColor)
    val logger = new SysoutLogger(logTag, color)
    colorLogger(streams.log).info(s"[YELLOW]Starting application ${formatAppName(project.project, color)} in the background ...")

    val appProcess = AppProcess(project, color, logger) {
      forkRun(options, theMainClass, cp.map(_.data), args ++ startConfig.startArgs, logger, startConfig.jvmArgs)
    }
    registerAppProcess(project, appProcess)
    appProcess
  }

  def stopAppWithStreams(streams: TaskStreams, project: ProjectRef): Unit =
    stopApp(colorLogger(streams.log), project)

  def stopApp(log: Logger, project: ProjectRef): Unit = {
    revolverState.getProcess(project) match {
      case Some(appProcess) if appProcess.isRunning =>
        log.info(s"[YELLOW]Stopping application ${formatApp(appProcess)} (by killing the forked JVM) ...")
        appProcess.stop()
      case Some(_) =>
        // Process exists but not running, just unregister
      case None =>
        log.info(s"[YELLOW]Application ${formatAppName(project.project, "[BOLD]")} not yet started")
    }
    unregisterAppProcess(project)
  }

  def stopApps(log: Logger): Unit =
    revolverState.runningProjects.foreach(stopApp(log, _))

  def showStatus(streams: TaskStreams, project: ProjectRef): Unit =
    colorLogger(streams.log).info {
      revolverState.getProcess(project).find(_.isRunning) match {
        case Some(appProcess) =>
          s"[GREEN]Application ${formatApp(appProcess, color = "[GREEN]")} is currently running"
        case None =>
          s"[YELLOW]Application ${formatAppName(project.project, "[BOLD]")} is currently NOT running"
      }
    }

  def updateState(f: RevolverState => RevolverState): Unit = GlobalState.update(f)
  def updateStateAndGet[T](f: RevolverState => (RevolverState, T)): T = GlobalState.updateAndGet(f)
  def revolverState: RevolverState = GlobalState.get()

  def registerAppProcess(project: ProjectRef, process: AppProcess): Unit =
    updateState { state =>
      val oldProcess = state.getProcess(project)
      if (oldProcess.exists(_.isRunning)) oldProcess.get.stop()
      state.addProcess(project, process)
    }

  def unregisterAppProcess(project: ProjectRef): Unit =
    updateState(_.removeProcessAndColor(project))

  case class ExtraCmdLineOptions(jvmArgs: Seq[String], startArgs: Seq[String])

  import complete.Parsers._
  import complete.Parser._

  private val spaceDelimitedWithoutDashes =
    (token(Space) ~> and(token(NotSpace, "<args>"), not("---", "Excluded."))).* <~ SpaceClass.*

  /** Parser for: <arg1> <arg2> ... --- <jvmArg1> <jvmArg2> ... */
  val startArgsParser: State => complete.Parser[ExtraCmdLineOptions] = { (_: State) =>
    (spaceDelimitedWithoutDashes ~ (SpaceClass.* ~ "---" ~ SpaceClass.* ~> spaceDelimited("<jvm-args>")).?) map {
      case (a, b) => ExtraCmdLineOptions(b.getOrElse(Nil), a)
    }
  }

  def formatApp(process: AppProcess, color: String = "[YELLOW]"): String =
    formatAppName(process.projectName, process.consoleColor, color)

  def formatAppName(projectName: String, projectColor: String, color: String = "[YELLOW]"): String =
    s"[RESET]$projectColor$projectName[RESET]$color"

  def forkRun(
      config: ForkOptions,
      mainClass: String,
      classpath: Seq[File],
      options: Seq[String],
      log: Logger,
      extraJvmArgs: Seq[String]
  ): Process = {
    log.info(options.mkString(s"Starting $mainClass.main(", ", ", ")"))
    val scalaOptions = "-classpath" :: Path.makeString(classpath) :: mainClass :: options.toList
    val newOptions = config
      .withOutputStrategy(config.outputStrategy.getOrElse(LoggedOutput(log)))
      .withRunJVMOptions(config.runJVMOptions ++ extraJvmArgs)

    Fork.java.fork(newOptions, scalaOptions)
  }
}
