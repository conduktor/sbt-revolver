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

package spray.revolver

import sbt._
import sbt.Keys._

import java.nio.file._
import java.nio.file.StandardWatchEventKinds._
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong, AtomicReference}
import scala.collection.mutable
import scala.concurrent.duration._

/**
 * Batch watch implementation for sbt-revolver.
 *
 * Provides a debounced watch mode that waits for file changes to settle
 * before triggering a restart. Useful when tools like Claude Code or IDEs
 * make multiple rapid file changes.
 *
 * Usage: reStartWatch
 *
 * Configure with:
 *   reBatchWindow := 5.seconds   // wait time after last change (default: 3s)
 */
object BatchWatch {
  import Utilities._

  def batchWatchCommand: Command = Command.command("reStartWatch") { initialState =>
    val extracted = Project.extract(initialState)
    val log = colorLogger(initialState.log)

    val batchWindow = extracted.getOpt(RevolverPlugin.autoImport.reBatchWindow).getOrElse(3.seconds)
    val baseDir = extracted.getOpt(baseDirectory).getOrElse(file("."))

    log.info(s"[CYAN]Starting batched watch mode (window: ${batchWindow.toSeconds}s)")
    log.info("[CYAN]Press 'q' + Enter to stop")
    println()

    // Run initial reStart
    var state = Command.process("reStart", initialState, _ => ())

    val running = new AtomicBoolean(true)
    val lastChangeTime = new AtomicLong(0L)
    val changedFiles = new AtomicReference[Set[String]](Set.empty)

    // Setup file watcher
    val watchService = FileSystems.getDefault.newWatchService()
    val registeredDirs = mutable.Set[Path]()

    def registerDirectory(dir: File): Unit = {
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

    // Register source directories
    Seq("src/main/scala", "src/main/java", "src/main/resources",
        "src/test/scala", "src/test/java", "src/test/resources")
      .map(baseDir / _)
      .filter(_.exists())
      .foreach(registerDirectory)

    // File watch thread
    val watchThread = new Thread(() => {
      while (running.get()) {
        try {
          val key = watchService.poll(200, TimeUnit.MILLISECONDS)
          if (key != null) {
            import scala.jdk.CollectionConverters._
            val events = key.pollEvents().asScala

            val relevantChanges = events.filter { event =>
              val name = event.context().toString
              name.endsWith(".scala") || name.endsWith(".java") ||
              name.endsWith(".conf") || name.endsWith(".properties")
            }

            if (relevantChanges.nonEmpty) {
              val now = System.currentTimeMillis()
              val newFiles = relevantChanges.map(_.context().toString).toSet

              lastChangeTime.set(now)
              changedFiles.updateAndGet(_ ++ newFiles)

              val totalFiles = changedFiles.get().size
              print(s"\r[revolver] ${totalFiles} file(s) changed, waiting ${batchWindow.toSeconds}s...          ")
            }

            key.reset()

            // Register any new directories
            events.filter(_.kind() == ENTRY_CREATE).foreach { event =>
              val dir = key.watchable().asInstanceOf[Path].resolve(event.context().asInstanceOf[Path]).toFile
              if (dir.isDirectory) registerDirectory(dir)
            }
          }
        } catch {
          case _: InterruptedException => // normal shutdown
          case e: Exception =>
            if (running.get()) log.warn(s"Watch error: ${e.getMessage}")
        }
      }
    }, "revolver-batch-watcher")
    watchThread.setDaemon(true)
    watchThread.start()

    // Main loop
    try {
      while (running.get()) {
        Thread.sleep(100)

        val lastChange = lastChangeTime.get()
        if (lastChange > 0) {
          val timeSinceLastChange = System.currentTimeMillis() - lastChange

          if (timeSinceLastChange >= batchWindow.toMillis) {
            val files = changedFiles.getAndSet(Set.empty)
            lastChangeTime.set(0L)

            println()
            log.info(s"[YELLOW]Restarting: ${files.size} file(s) changed")
            files.take(5).foreach(f => log.info(s"[CYAN]  - $f"))
            if (files.size > 5) log.info(s"[CYAN]  ... and ${files.size - 5} more")

            state = Command.process("reStart", state, _ => ())
            println()
          }
        }

        // Check for quit command
        if (System.in.available() > 0) {
          val c = System.in.read()
          if (c == 'q' || c == 'Q') {
            running.set(false)
          }
        }
      }
    } catch {
      case _: InterruptedException => // normal shutdown
    } finally {
      running.set(false)
      watchService.close()
      println()
      log.info("[CYAN]Batch watch stopped")
    }

    state
  }
}
