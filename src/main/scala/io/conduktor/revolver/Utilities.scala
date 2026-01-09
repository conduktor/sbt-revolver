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

import scala.Console._

object Utilities {

  def colorLogger(state: State): Logger = colorLogger(state.log)

  def colorLogger(logger: Logger): Logger = new Logger {
    def trace(t: => Throwable): Unit = logger.trace(t)
    def success(message: => String): Unit = logger.success(message)
    def log(level: Level.Value, message: => String): Unit =
      logger.log(level, colorize(message))
  }

  private val simpleColors = Seq(
    "RED" -> RED,
    "GREEN" -> GREEN,
    "YELLOW" -> YELLOW,
    "BLUE" -> BLUE,
    "MAGENTA" -> MAGENTA,
    "CYAN" -> CYAN,
    "WHITE" -> WHITE
  )

  private val ansiTagMapping: Seq[(String, String)] = {
    val base = Seq("BOLD" -> BOLD, "RESET" -> RESET)
    val reversed = simpleColors.map { case (name, code) => s"~$name" -> s"$code$REVERSED" }
    val underlined = simpleColors.map { case (name, code) => s"_$name" -> s"$code$UNDERLINED" }

    (base ++ simpleColors ++ reversed ++ underlined)
      .map { case (name, code) => s"[$name]" -> code }
  }

  def colorize(message: String): String =
    ansiTagMapping.foldLeft(message) { case (msg, (tag, code)) =>
      msg.replace(tag, code)
    }
}
