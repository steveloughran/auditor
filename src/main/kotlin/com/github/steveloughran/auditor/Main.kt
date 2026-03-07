package com.github.steveloughran.auditor

import java.nio.file.Path
import kotlin.system.exitProcess

fun main(args: Array<String>) {
  val argList = args.toMutableList()
  val format = extractOption(argList, "-format")?.let {
    try {
      OutputFormat.parse(it)
    } catch (e: IllegalArgumentException) {
      System.err.println(e.message)
      exitProcess(1)
    }
  } ?: OutputFormat.TEXT

  val level = extractOption(argList, "-level")?.let {
    try {
      AuditLevel.parse(it)
    } catch (e: IllegalArgumentException) {
      System.err.println(e.message)
      exitProcess(1)
    }
  } ?: AuditLevel.STRUCTURAL

  if (argList.size != 2) {
    System.err.println("Usage: auditor [-format text|csv|markdown] [-level 1|2|3] <reference.jar> <target.jar>")
    System.err.println()
    System.err.println("  -format        Output format: text (default), csv, markdown")
    System.err.println("  -level         Audit level: 1=structural (default), 2=bytecode, 3=semantic")
    System.err.println("  reference.jar  JAR compiled from trusted source")
    System.err.println("  target.jar     JAR to audit (e.g. downloaded binary)")
    exitProcess(1)
  }

  val referencePath = Path.of(argList[0])
  val targetPath = Path.of(argList[1])

  if (!referencePath.toFile().exists()) {
    System.err.println("Reference JAR not found: $referencePath")
    exitProcess(1)
  }
  if (!targetPath.toFile().exists()) {
    System.err.println("Target JAR not found: $targetPath")
    exitProcess(1)
  }

  val refMd5 = JarAnalyzer.md5(referencePath)
  val tgtMd5 = JarAnalyzer.md5(targetPath)
  System.err.println("Reference: $referencePath (MD5: $refMd5)")
  System.err.println("Target:    $targetPath (MD5: $tgtMd5)")

  if (refMd5 == tgtMd5) {
    System.err.println()
    System.err.println("JARs are byte-identical (MD5 match). No further analysis needed.")
    exitProcess(0)
  }

  System.err.println()
  System.err.println("MD5 checksums differ. Performing ${level.description}...")

  val reference = JarAnalyzer.analyze(referencePath, level)
  val target = JarAnalyzer.analyze(targetPath, level)

  val report = ClassComparator.compare(reference, target, level)
  print(report.format(format))

  exitProcess(if (report.isMatch) 0 else 1)
}

private fun extractOption(args: MutableList<String>, name: String): String? {
  val index = args.indexOf(name)
  if (index < 0) return null
  if (index + 1 >= args.size) {
    System.err.println("Option $name requires a value")
    exitProcess(1)
  }
  val value = args[index + 1]
  args.removeAt(index + 1)
  args.removeAt(index)
  return value
}
