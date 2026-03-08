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
    System.err.println("Usage: auditor [-format text|csv|markdown] [-level 1|2|3] <reference> <target>")
    System.err.println()
    System.err.println("  -format    Output format: text (default), csv, markdown")
    System.err.println("  -level     Audit level: 1=structural (default), 2=bytecode, 3=semantic")
    System.err.println("  reference  JAR file or directory of JARs (trusted source)")
    System.err.println("  target     JAR file or directory of JARs (under audit)")
    exitProcess(1)
  }

  val referencePath = Path.of(argList[0])
  val targetPath = Path.of(argList[1])

  if (!referencePath.toFile().exists()) {
    System.err.println("Reference not found: $referencePath")
    exitProcess(1)
  }
  if (!targetPath.toFile().exists()) {
    System.err.println("Target not found: $targetPath")
    exitProcess(1)
  }

  val refIsDir = referencePath.toFile().isDirectory
  val tgtIsDir = targetPath.toFile().isDirectory

  if (refIsDir != tgtIsDir) {
    System.err.println("Both arguments must be either files or directories")
    exitProcess(1)
  }

  if (refIsDir) {
    compareDirectories(referencePath, targetPath, level, format)
  } else {
    compareJars(referencePath, targetPath, level, format)
  }
}

private fun compareJars(
  referencePath: Path,
  targetPath: Path,
  level: AuditLevel,
  format: OutputFormat,
) {
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

private fun compareDirectories(
  referenceDir: Path,
  targetDir: Path,
  level: AuditLevel,
  format: OutputFormat,
) {
  System.err.println("Reference directory: $referenceDir")
  System.err.println("Target directory:    $targetDir")
  System.err.println("Audit level:         ${level.description}")
  System.err.println()

  val report = DirectoryScanner.compareDirectories(referenceDir, targetDir, level)

  System.err.println("Found ${report.referenceJarCount} JAR(s) in reference, ${report.targetJarCount} in target")
  System.err.println()

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
