# sbt-revolver

An SBT plugin for fast development turnaround in Scala applications.

**This is a [Conduktor](https://conduktor.io) fork** with additional features for AI-assisted development workflows.

## Features

* Start and stop your application in the background of your interactive SBT shell (forked JVM)
* Triggered restart: automatically restart when source files change (`~reStart`)
* **Batched restart**: debounced watch mode that waits for file changes to settle before restarting — ideal for tools like Claude Code that make multiple rapid changes (`reStartWatch`)

## Installation

Requires SBT 1.x. Add to `project/plugins.sbt`:

```scala
addSbtPlugin("io.conduktor" % "sbt-revolver" % "0.11.0")
```

sbt-revolver is an auto plugin — no additional configuration needed. In multi-module builds, disable for specific submodules with `Project(...).disablePlugins(RevolverPlugin)`.

## Usage

### Commands

* **`reStart <args> --- <jvmArgs>`** — Starts your application in a forked JVM. Optional arguments are appended to `reStartArgs` / `reStart / javaOptions`. Restarts if already running.

* **`reStop`** — Stops the application by killing the forked JVM.

* **`reStatus`** — Shows current running state.

### Triggered Restart

```
sbt> ~reStart
```

Watches for source changes and automatically restarts. Press Enter to stop watching.

### Batched Restart

When using tools like **Claude Code** that make multiple rapid file changes, `~reStart` triggers unnecessary restarts. Use `reStartWatch` instead:

```
sbt> reStartWatch
```

This waits for the batch window (default: 3s) after the **last** file change before restarting.

```
Claude modifies file A → timer starts (3s)
Claude modifies file B → timer resets (3s)
Claude modifies file C → timer resets (3s)
Claude is done         → 3s pass → single restart
```

Configure in `build.sbt`:

```scala
import scala.concurrent.duration._
reBatchWindow := 5.seconds  // default: 3s
```

Press `q` + Enter to stop.

## Configuration

| Setting | Type | Description |
|---------|------|-------------|
| `reStartArgs` | `Seq[String]` | Arguments passed to main method on every start |
| `reStart / mainClass` | `Option[String]` | Main class (defaults to `Compile / run / mainClass`) |
| `reStart / javaOptions` | `Seq[String]` | JVM options for the forked process |
| `reStart / baseDirectory` | `File` | Working directory |
| `reStart / fullClasspath` | `Classpath` | Classpath for running |
| `reStart / envVars` | `Map[String, String]` | Environment variables |
| `reColors` | `Seq[String]` | Colors for tagging output in multi-module projects |
| `reLogTag` | `String` | Log tag prefix (default: project name) |
| `reBatchWindow` | `FiniteDuration` | Debounce window for `reStartWatch` (default: 3s) |

### Examples

```scala
// 2 GB memory limit
reStart / javaOptions += "-Xmx2g"

// Custom main class
reStart / mainClass := Some("com.example.Main")

// Fixed start arguments
reStartArgs := Seq("-x")

// Environment variables
reStart / envVars := Map("USER_TOKEN" -> "secret")

// Disable colors in multi-module
reColors := Revolver.noColors

// Longer batch window for slow tools
reBatchWindow := 10.seconds
```

## License

Apache License 2.0
