// Suppress airframe LogManager warning from sbt-sonatype
object LogManagerFix {
  sys.props("java.util.logging.manager") = "java.util.logging.LogManager"
}
