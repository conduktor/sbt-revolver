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

import sbt.{Logger, ProjectRef}

import java.lang.{Runtime => JRuntime}
import scala.sys.process.Process

/** Token stored in SBT state to hold the Process of a background application. */
case class AppProcess(projectRef: ProjectRef, consoleColor: String, log: Logger)(process: Process) {

  @volatile private var finishState: Option[Int] = None

  private val shutdownHook: Thread = new Thread(() => {
    if (isRunning) {
      log.info("... killing ...")
      process.destroy()
    }
  })

  // Start background thread to monitor process exit
  locally {
    val thread = new Thread(() => {
      val code = process.exitValue()
      finishState = Some(code)
      log.info(s"... finished with exit code $code")
      unregisterShutdownHook()
      Actions.unregisterAppProcess(projectRef)
    })
    thread.start()
  }

  registerShutdownHook()

  def projectName: String = projectRef.project

  def isRunning: Boolean = finishState.isEmpty

  def stop(): Unit = {
    unregisterShutdownHook()
    process.destroy()
    process.exitValue()
  }

  private def registerShutdownHook(): Unit =
    JRuntime.getRuntime.addShutdownHook(shutdownHook)

  private def unregisterShutdownHook(): Unit =
    JRuntime.getRuntime.removeShutdownHook(shutdownHook)
}
