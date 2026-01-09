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

import sbt._
import sbt.Keys._
import Actions._
import Utilities._

import scala.concurrent.duration._

object RevolverPlugin extends AutoPlugin {

  object autoImport extends RevolverKeys {
    object Revolver {
      val noColors: Seq[String] = Nil
      val basicColors: Seq[String] = Seq("BLUE", "MAGENTA", "CYAN", "YELLOW", "GREEN")
    }
  }

  import autoImport._

  override def requires: Plugins = sbt.plugins.JvmPlugin
  override def trigger: PluginTrigger = allRequirements

  override def globalSettings: Seq[Setting[_]] = Seq(
    reStartArgs := Seq.empty,
    reColors := Revolver.basicColors,
    reBatchWindow := 3.seconds,
    commands += BatchWatch.batchWatchCommand
  )

  override def projectSettings: Seq[Setting[_]] = Seq(
    reStart / mainClass := (Compile / run / mainClass).value,
    reStart / fullClasspath := (Runtime / fullClasspath).value,
    reLogTag := thisProjectRef.value.project,

    reStart := Def.inputTask {
      restartApp(
        streams.value,
        reLogTag.value,
        thisProjectRef.value,
        reForkOptions.value,
        (reStart / mainClass).value,
        (reStart / fullClasspath).value,
        reStartArgs.value,
        startArgsParser.parsed
      )
    }.dependsOn(Compile / products).evaluated,

    reStop := stopAppWithStreams(streams.value, thisProjectRef.value),

    reStatus := showStatus(streams.value, thisProjectRef.value),

    reForkOptions := {
      taskTemporaryDirectory.value
      ForkOptions(
        javaHome = javaHome.value,
        outputStrategy = outputStrategy.value,
        bootJars = Vector.empty[File],
        workingDirectory = Some((reStart / baseDirectory).value),
        runJVMOptions = (reStart / javaOptions).value.toVector,
        connectInput = false,
        envVars = (reStart / envVars).value
      )
    },

    // Stop running app when project is reloaded
    Global / onUnload := { state =>
      stopApps(colorLogger(state))
      (Global / onUnload).value(state)
    },

    Global / onLoad := { state =>
      val colorTags = reColors.value.map(c => s"[${c.toUpperCase}]")
      GlobalState.update(_.copy(colorPool = collection.immutable.Queue(colorTags: _*)))
      (Global / onLoad).value(state)
    }
  )
}
