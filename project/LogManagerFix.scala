// Suppress airframe LogManager warning from sbt-sonatype
// This runs at project load time, before sonatype plugin initializes
object LogManagerFix {
  sys.props("java.util.logging.manager") = "java.util.logging.LogManager"
}
