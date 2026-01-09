name := "sbt-revolver"

organization := "io.conduktor"

description := "An SBT plugin for dangerously fast development turnaround in Scala (Conduktor fork with batch restart support)"

startYear := Some(2011)

homepage := Some(url("https://github.com/conduktor/sbt-revolver"))

organizationHomepage := Some(url("https://conduktor.io"))

licenses += "Apache License 2.0" -> url("https://github.com/conduktor/sbt-revolver/raw/master/LICENSE")

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  Some {
    if (version.value.trim.contains("+")) "snapshots" at nexus + "content/repositories/snapshots"
    else                                  "releases"  at nexus + "service/local/staging/deploy/maven2"
  }
}

publishMavenStyle := true
Test / publishArtifact := false
pomIncludeRepository := { _ => false }

scmInfo := Some(
  ScmInfo(
    browseUrl = url("https://github.com/conduktor/sbt-revolver"),
    connection = "scm:git:git@github.com:conduktor/sbt-revolver.git"
  )
)

developers := List(
  Developer(
    "conduktor",
    "Conduktor",
    "dev@conduktor.io",
    url("https://github.com/conduktor")),
  Developer(
    "sbt-revolver-contributors",
    "Sbt Revolver Contributors",
    "",
    url("https://github.com/spray/sbt-revolver/graphs/contributors"))
)
