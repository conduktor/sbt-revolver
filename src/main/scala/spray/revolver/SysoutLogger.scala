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

/** Logger that writes directly to stdout, for use when streams are unavailable */
class SysoutLogger(appName: String, color: String) extends Logger {

  def trace(t: => Throwable): Unit = {
    t.printStackTrace()
    println(t)
  }

  def success(message: => String): Unit =
    println(Utilities.colorize(s"$color$appName[RESET] success: ") + message)

  def log(level: Level.Value, message: => String): Unit = {
    val levelStr = level match {
      case Level.Info  => ""
      case Level.Error => "[ERROR]"
      case other       => other.toString
    }
    println(Utilities.colorize(s"$color$appName[RESET]$levelStr ") + message)
  }
}
