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

import sbt.ProjectRef

import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec
import scala.collection.immutable.Queue

case class RevolverState(
    processes: Map[ProjectRef, AppProcess],
    colorPool: Queue[String]
) {

  def addProcess(project: ProjectRef, process: AppProcess): RevolverState =
    copy(processes = processes + (project -> process))

  def removeProcessAndColor(project: ProjectRef): RevolverState =
    getProcess(project) match {
      case Some(process) => removeProcess(project).offerColor(process.consoleColor)
      case None          => this
    }

  def exists(project: ProjectRef): Boolean = processes.contains(project)

  def runningProjects: Seq[ProjectRef] = processes.keys.toSeq

  def getProcess(project: ProjectRef): Option[AppProcess] = processes.get(project)

  def takeColor: (RevolverState, String) =
    if (colorPool.nonEmpty) {
      val (color, nextPool) = colorPool.dequeue
      (copy(colorPool = nextPool), color)
    } else (this, "")

  def offerColor(color: String): RevolverState =
    if (color.nonEmpty) copy(colorPool = colorPool.enqueue(color))
    else this

  private def removeProcess(project: ProjectRef): RevolverState =
    copy(processes = processes - project)
}

object RevolverState {
  def initial: RevolverState = RevolverState(Map.empty, Queue.empty)
}

/** Thread-safe global state manager using CAS operations. */
object GlobalState {
  private val state = new AtomicReference(RevolverState.initial)

  @tailrec
  def update(f: RevolverState => RevolverState): RevolverState = {
    val original = state.get()
    val updated = f(original)
    if (state.compareAndSet(original, updated)) updated
    else update(f)
  }

  @tailrec
  def updateAndGet[T](f: RevolverState => (RevolverState, T)): T = {
    val original = state.get()
    val (updated, value) = f(original)
    if (state.compareAndSet(original, updated)) value
    else updateAndGet(f)
  }

  def get(): RevolverState = state.get()
}
