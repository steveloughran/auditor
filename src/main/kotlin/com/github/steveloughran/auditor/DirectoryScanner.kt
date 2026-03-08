package com.github.steveloughran.auditor

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile

/**
 * Recursively scans directories for JAR files and matches them
 * by relative path for comparison.
 */
object DirectoryScanner {

  /**
   * Recursively find all .jar files under a directory,
   * returning a map of relative path (string) to absolute path.
   */
  fun findJars(root: Path): Map<String, Path> {
    val result = mutableMapOf<String, Path>()
    Files.walk(root).use { stream ->
      stream
        .filter { it.isRegularFile() && it.extension == "jar" }
        .forEach { path ->
          val relative = root.relativize(path).toString()
          result[relative] = path
        }
    }
    return result
  }

  data class JarPairResult(
    val relativePath: String,
    val status: JarPairStatus,
    val report: ComparisonReport? = null,
    val md5Match: Boolean = false,
  )

  enum class JarPairStatus(val text: String) {
    IDENTICAL("identical"),
    MATCH("match"),
    DIFFERENT("different"),
    ONLY_IN_REFERENCE("only in reference"),
    ONLY_IN_TARGET("only in target"),
  }

  /**
   * Compare all JARs in two directory trees by relative filename.
   */
  fun compareDirectories(
    referenceDir: Path,
    targetDir: Path,
    level: AuditLevel,
  ): DirectoryReport {
    val refJars = findJars(referenceDir)
    val tgtJars = findJars(targetDir)
    val results = mutableListOf<JarPairResult>()

    val onlyInRef = refJars.keys - tgtJars.keys
    val onlyInTgt = tgtJars.keys - refJars.keys
    val common = refJars.keys.intersect(tgtJars.keys)

    for (name in onlyInRef.sorted()) {
      results.add(JarPairResult(name, JarPairStatus.ONLY_IN_REFERENCE))
    }
    for (name in onlyInTgt.sorted()) {
      results.add(JarPairResult(name, JarPairStatus.ONLY_IN_TARGET))
    }
    for (name in common.sorted()) {
      val refPath = refJars[name]!!
      val tgtPath = tgtJars[name]!!

      val refMd5 = JarAnalyzer.md5(refPath)
      val tgtMd5 = JarAnalyzer.md5(tgtPath)

      if (refMd5 == tgtMd5) {
        results.add(JarPairResult(name, JarPairStatus.IDENTICAL, md5Match = true))
      } else {
        val refClasses = JarAnalyzer.analyze(refPath, level)
        val tgtClasses = JarAnalyzer.analyze(tgtPath, level)
        val report = ClassComparator.compare(refClasses, tgtClasses, level)
        val status = if (report.isMatch) JarPairStatus.MATCH else JarPairStatus.DIFFERENT
        results.add(JarPairResult(name, status, report))
      }
    }

    return DirectoryReport(
      referenceDir = referenceDir,
      targetDir = targetDir,
      referenceJarCount = refJars.size,
      targetJarCount = tgtJars.size,
      results = results,
    )
  }
}

data class DirectoryReport(
  val referenceDir: Path,
  val targetDir: Path,
  val referenceJarCount: Int,
  val targetJarCount: Int,
  val results: List<DirectoryScanner.JarPairResult>,
) {
  val isMatch: Boolean get() = results.all {
    it.status == DirectoryScanner.JarPairStatus.IDENTICAL ||
      it.status == DirectoryScanner.JarPairStatus.MATCH
  }

  fun format(outputFormat: OutputFormat): String = when (outputFormat) {
    OutputFormat.TEXT -> text()
    OutputFormat.CSV -> csv()
    OutputFormat.MARKDOWN -> markdown()
  }

  fun text(): String = buildString {
    appendLine("Directory Comparison Report")
    appendLine("===========================")
    appendLine("Reference: $referenceDir ($referenceJarCount JARs)")
    appendLine("Target:    $targetDir ($targetJarCount JARs)")
    appendLine()
    for (result in results) {
      appendLine("${result.relativePath}: ${result.status.text}")
      if (result.report != null && !result.report.isMatch) {
        for (diff in result.report.differences) {
          appendLine("    [${diff.type.text}] ${diff.className}: ${diff.symbol}")
        }
      }
    }
    appendLine()
    val diffCount = results.count {
      it.status != DirectoryScanner.JarPairStatus.IDENTICAL &&
        it.status != DirectoryScanner.JarPairStatus.MATCH
    }
    if (diffCount == 0) {
      appendLine("OK: All JARs match.")
    } else {
      appendLine("$diffCount JAR(s) with differences.")
    }
  }

  fun csv(): String = buildString {
    appendLine("\"jar\",\"class\",\"change\",\"symbol\"")
    for (result in results) {
      when (result.status) {
        DirectoryScanner.JarPairStatus.ONLY_IN_REFERENCE ->
          appendLine("${csvField(result.relativePath)},\"\",\"only in reference\",\"\"")
        DirectoryScanner.JarPairStatus.ONLY_IN_TARGET ->
          appendLine("${csvField(result.relativePath)},\"\",\"only in target\",\"\"")
        DirectoryScanner.JarPairStatus.IDENTICAL -> {}
        DirectoryScanner.JarPairStatus.MATCH -> {}
        DirectoryScanner.JarPairStatus.DIFFERENT -> {
          for (diff in result.report!!.differences) {
            appendLine("${csvField(result.relativePath)},${csvField(diff.className)},${csvField(diff.type.text)},${csvField(diff.symbol)}")
          }
        }
      }
    }
  }

  fun markdown(): String = buildString {
    appendLine("## Directory Comparison Report")
    appendLine()
    appendLine("| Metric | Value |")
    appendLine("|--------|-------|")
    appendLine("| Reference | `$referenceDir` ($referenceJarCount JARs) |")
    appendLine("| Target | `$targetDir` ($targetJarCount JARs) |")
    appendLine()
    appendLine("### JAR Summary")
    appendLine()
    appendLine("| JAR | Status |")
    appendLine("|-----|--------|")
    for (result in results) {
      appendLine("| `${result.relativePath}` | ${result.status.text} |")
    }

    val diffResults = results.filter {
      it.status == DirectoryScanner.JarPairStatus.DIFFERENT ||
        it.status == DirectoryScanner.JarPairStatus.ONLY_IN_REFERENCE ||
        it.status == DirectoryScanner.JarPairStatus.ONLY_IN_TARGET
    }
    if (diffResults.any { it.report != null && !it.report.isMatch }) {
      appendLine()
      appendLine("### Differences")
      appendLine()
      appendLine("| JAR | class | change | symbol |")
      appendLine("|-----|-------|--------|--------|")
      for (result in diffResults) {
        if (result.report != null) {
          for (diff in result.report.differences) {
            val sym = if (diff.symbol.isNotEmpty()) "`${mdEscape(diff.symbol)}`" else ""
            appendLine("| `${result.relativePath}` | `${mdEscape(diff.className)}` | ${diff.type.text} | $sym |")
          }
        }
      }
    }
  }

  private fun csvField(value: String): String {
    return if (value.contains(',') || value.contains('"') || value.contains('\n')) {
      "\"${value.replace("\"", "\"\"")}\""
    } else {
      "\"$value\""
    }
  }

  private fun mdEscape(value: String): String = value.replace("|", "\\|")
}
