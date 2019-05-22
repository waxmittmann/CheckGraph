package cmri.procan.checkgraph.utils

object StringUtils {
  def indent(str: String, by: Int = 3): String =
    str.split("\n").map(line => s"${" ".take(by)}$line").mkString("\n")

  def shortenString(s: String): String =
    if (s.toString.length > 50)
      s"excerpt: ${s.take(25).replace("\n", " ")} ... " +
        s"${s.takeRight(25).replace("\n", " ")}"
    else s
}
