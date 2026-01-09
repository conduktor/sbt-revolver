name := "sbt-revolver"
organization := "io.conduktor"
description := "SBT plugin for fast development turnaround in Scala (Conduktor fork with batch restart)"

startYear := Some(2011)
homepage := Some(url("https://github.com/conduktor/sbt-revolver"))
licenses := Seq("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0"))

scmInfo := Some(
  ScmInfo(
    url("https://github.com/conduktor/sbt-revolver"),
    "scm:git:git@github.com:conduktor/sbt-revolver.git"
  )
)

developers := List(
  Developer("conduktor", "Conduktor", "dev@conduktor.io", url("https://conduktor.io")),
  Developer("jrudolph", "Johannes Rudolph", "", url("https://github.com/jrudolph")),
  Developer("sirthias", "Mathias Doenitz", "", url("https://github.com/sirthias"))
)

// sbt-ci-release settings
ThisBuild / sonatypeCredentialHost := "s01.oss.sonatype.org"
ThisBuild / versionScheme := Some("early-semver")
