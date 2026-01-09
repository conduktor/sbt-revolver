enablePlugins(SbtPlugin)

scalacOptions := Seq(
  "-deprecation",
  "-encoding", "utf8",
  "-feature",
  "-unchecked",
  "-Xlint:adapted-args",
  "-Xlint:infer-any",
  "-Xlint:nullary-unit",
  "-Xlint:private-shadow",
  "-Xlint:type-parameter-shadow",
  "-Ywarn-dead-code",
  "-Ywarn-unused:imports"
)

scriptedLaunchOpts += s"-Dplugin.version=${version.value}"
scriptedBufferLog := false
Test / test := (Test / test).dependsOn(scripted.toTask("")).value
