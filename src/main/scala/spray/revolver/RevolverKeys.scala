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

package spray.revolver

import sbt._

import scala.concurrent.duration._

trait RevolverKeys {

  val reStart = InputKey[AppProcess]("re-start",
    "Starts the application in a forked JVM. If already running, it is first stopped then restarted.")

  val reStop = TaskKey[Unit]("re-stop",
    "Stops the application if it is currently running in the background")

  val reStatus = TaskKey[Unit]("re-status",
    "Shows information about the current running state of the application")

  val reStartArgs = SettingKey[Seq[String]]("re-start-args",
    "Arguments passed to the application's main method on every start")

  val reForkOptions = TaskKey[ForkOptions]("re-fork-options",
    "Fork options for the start task")

  val reColors = SettingKey[Seq[String]]("re-colors",
    "Colors used for tagging output from different processes")

  val reLogTag = SettingKey[String]("re-log-tag",
    "Tag shown in front of log messages. Default: project name")

  val reBatchWindow = SettingKey[FiniteDuration]("re-batch-window",
    "Time to wait after last file change before triggering restart (for reStartWatch). Default: 3 seconds")
}
