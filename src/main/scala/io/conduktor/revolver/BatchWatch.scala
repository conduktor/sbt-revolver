/*
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

import sbt._
import sbt.Keys._

import java.nio.file._
import java.nio.file.StandardWatchEventKinds._
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong, AtomicReference}
import scala.collection.mutable
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

/**
 * Debounced file watcher for sbt-revolver.
 *
 * Waits for file changes to settle before triggering restart.
 * Ideal for tools like Claude Code that make rapid successive changes.
 */
object BatchWatch {
  import Utilities._

  private val WatchedExtensions = Set(".scala", ".java", ".conf", ".properties")
  private val SourcePaths = Seq(
    "src/main/scala", "src/main/java", "src/main/resources",
    "src/test/scala", "src/test/java", "src/test/resources"
  )

  /**
   * Command that watches for file changes and restarts with debouncing.
   * Usage: reStartWatch [project]
   * Example: reStartWatch app
   *
   * Watches all source directories in the build (not just the target project)
   * so changes to dependencies also trigger restarts.
   */
  def batchWatchCommand: Command = Command.single("reStartWatch") { (initialState, projectArg) =>
    val extracted = Project.extract(initialState)
    val log = colorLogger(initialState.log)

    // Get build root for watching all sources
    val buildRoot = extracted.get(LocalRootProject / baseDirectory)

    // Switch to specified project if provided (for reStart command)
    val (state, projectPrefix) = if (projectArg.nonEmpty) {
      val newState = Command.process(s"project $projectArg", initialState, _ => ())
      (newState, s"$projectArg/")
    } else {
      (initialState, "")
    }

    val newExtracted = Project.extract(state)
    val batchWindow = newExtracted.getOpt(RevolverPlugin.autoImport.reBatchWindow).getOrElse(3.seconds)

    log.info(s"[CYAN]Starting batched watch mode (window: ${batchWindow.toSeconds}s)")
    if (projectArg.nonEmpty) log.info(s"[CYAN]Project: $projectArg")
    log.info(s"[CYAN]Watching: $buildRoot")
    log.info("[CYAN]Press 'q' + Enter to stop")
    println()

    // Watch from build root to catch all module changes
    val watcher = new FileWatcher(buildRoot, log)
    val reStartCmd = s"${projectPrefix}reStart"
    var currentState = Command.process(reStartCmd, state, _ => ())

    try {
      currentState = runWatchLoop(currentState, watcher, batchWindow, log, reStartCmd)
    } finally {
      watcher.close()
      println()
      log.info("[CYAN]Batch watch stopped")
    }

    currentState
  }

  private def runWatchLoop(
      initialState: State,
      watcher: FileWatcher,
      batchWindow: FiniteDuration,
      log: Logger,
      reStartCmd: String
  ): State = {
    var state = initialState

    while (watcher.isRunning) {
      Thread.sleep(100)

      watcher.checkForRestart(batchWindow).foreach { files =>
        logChangedFiles(log, files)
        state = Command.process(reStartCmd, state, _ => ())
        println()
      }

      checkForQuit(watcher)
    }

    state
  }

  private def logChangedFiles(log: Logger, files: Set[String]): Unit = {
    println()
    log.info(s"[YELLOW]Restarting: ${files.size} file(s) changed")
    files.take(5).foreach(f => log.info(s"[CYAN]  - $f"))
    if (files.size > 5) log.info(s"[CYAN]  ... and ${files.size - 5} more")
  }

  private def checkForQuit(watcher: FileWatcher): Unit = {
    if (System.in.available() > 0) {
      val c = System.in.read()
      if (c == 'q' || c == 'Q') watcher.stop()
    }
  }

  /** Encapsulates file watching state and logic */
  private class FileWatcher(baseDir: File, log: Logger) {
    private val running = new AtomicBoolean(true)
    private val lastChangeTime = new AtomicLong(0L)
    private val changedFiles = new AtomicReference[Set[String]](Set.empty)
    private val watchService = FileSystems.getDefault.newWatchService()
    private val registeredDirs = mutable.Set[Path]()

    // Find and register all source directories recursively (handles multi-module builds)
    findSourceDirectories(baseDir).foreach(registerDirectory)
    startWatchThread()

    private def findSourceDirectories(root: File): Seq[File] = {
      val sourcePatterns = SourcePaths.map(_.split("/").toList)

      def findMatching(dir: File, depth: Int): Seq[File] = {
        if (!dir.isDirectory) return Seq.empty

        val matchedSources = sourcePatterns.flatMap { pattern =>
          val candidate = pattern.foldLeft(dir)(_ / _)
          if (candidate.exists() && candidate.isDirectory) Some(candidate) else None
        }

        val subdirs = Option(dir.listFiles())
          .getOrElse(Array.empty)
          .filter(f => f.isDirectory && !f.getName.startsWith(".") && f.getName != "target")
          .flatMap(subdir => findMatching(subdir, depth + 1))

        matchedSources ++ subdirs
      }

      findMatching(root, 0).distinct
    }

    def isRunning: Boolean = running.get()
    def stop(): Unit = running.set(false)
    def close(): Unit = {
      running.set(false)
      watchService.close()
    }

    def checkForRestart(batchWindow: FiniteDuration): Option[Set[String]] = {
      val lastChange = lastChangeTime.get()
      if (lastChange > 0 && System.currentTimeMillis() - lastChange >= batchWindow.toMillis) {
        val files = changedFiles.getAndSet(Set.empty)
        lastChangeTime.set(0L)
        Some(files)
      } else None
    }

    private def registerDirectory(dir: File): Unit = {
      if (dir.isDirectory && !registeredDirs.contains(dir.toPath)) {
        try {
          dir.toPath.register(watchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE)
          registeredDirs += dir.toPath
          Option(dir.listFiles()).foreach(_.filter(_.isDirectory).foreach(registerDirectory))
        } catch {
          case _: Exception => // ignore unregisterable dirs
        }
      }
    }

    private def startWatchThread(): Unit = {
      val thread = new Thread(() => {
        while (running.get()) {
          try {
            pollForChanges()
          } catch {
            case _: InterruptedException => // shutdown
            case e: Exception => if (running.get()) log.warn(s"Watch error: ${e.getMessage}")
          }
        }
      }, "revolver-batch-watcher")
      thread.setDaemon(true)
      thread.start()
    }

    private def pollForChanges(): Unit = {
      val key = watchService.poll(200, TimeUnit.MILLISECONDS)
      if (key != null) {
        val events = key.pollEvents().asScala

        val relevantChanges = events.filter { event =>
          val name = event.context().toString
          WatchedExtensions.exists(name.endsWith)
        }

        if (relevantChanges.nonEmpty) {
          lastChangeTime.set(System.currentTimeMillis())
          changedFiles.updateAndGet(_ ++ relevantChanges.map(_.context().toString).toSet)
          val total = changedFiles.get().size
          print(s"\r[revolver] $total file(s) changed, waiting...          ")
        }

        key.reset()

        // Register new directories
        events.filter(_.kind() == ENTRY_CREATE).foreach { event =>
          val dir = key.watchable().asInstanceOf[Path].resolve(event.context().asInstanceOf[Path]).toFile
          if (dir.isDirectory) registerDirectory(dir)
        }
      }
    }
  }
}
