package com.github.steveloughran.auditor

/**
 * Compares two sets of class structures and produces a report of differences.
 */
object ClassComparator {

  /**
   * Compare two maps of class structures (as returned by [JarAnalyzer.analyze]).
   * [reference] is the trusted set (e.g. locally compiled from source).
   * [target] is the set under audit (e.g. downloaded binary).
   */
  fun compare(
    reference: Map<String, ClassStructure>,
    target: Map<String, ClassStructure>,
    level: AuditLevel = AuditLevel.STRUCTURAL,
  ): ComparisonReport {
    val differences = mutableListOf<ClassDifference>()

    val onlyInReference = reference.keys - target.keys
    val onlyInTarget = target.keys - reference.keys
    val common = reference.keys.intersect(target.keys)

    for (name in onlyInReference) {
      differences.add(ClassDifference(name, DifferenceType.MISSING_IN_TARGET, ""))
    }
    for (name in onlyInTarget) {
      differences.add(ClassDifference(name, DifferenceType.EXTRA_IN_TARGET, ""))
    }
    for (name in common.sorted()) {
      differences.addAll(compareClasses(name, reference[name]!!, target[name]!!, level))
    }

    return ComparisonReport(
      referenceClassCount = reference.size,
      targetClassCount = target.size,
      differences = differences,
    )
  }

  private fun compareClasses(
    name: String,
    ref: ClassStructure,
    tgt: ClassStructure,
    level: AuditLevel,
  ): List<ClassDifference> {
    val diffs = mutableListOf<ClassDifference>()

    if (ref.superClass != tgt.superClass) {
      diffs.add(ClassDifference(name, DifferenceType.SUPERCLASS_CHANGED,
        "${ref.superClass} -> ${tgt.superClass}"))
    }
    if (ref.interfaces.sorted() != tgt.interfaces.sorted()) {
      diffs.add(ClassDifference(name, DifferenceType.INTERFACES_CHANGED,
        "${ref.interfaces.sorted()} -> ${tgt.interfaces.sorted()}"))
    }

    compareMethods(name, ref.methods, tgt.methods, diffs, level)
    compareFields(name, ref.fields, tgt.fields, diffs)

    return diffs
  }

  private fun compareMethods(
    className: String,
    refMethods: List<ClassStructure.MethodInfo>,
    tgtMethods: List<ClassStructure.MethodInfo>,
    diffs: MutableList<ClassDifference>,
    level: AuditLevel,
  ) {
    val refByKey = refMethods.associateBy { it.name to it.descriptor }
    val tgtByKey = tgtMethods.associateBy { it.name to it.descriptor }

    for (key in refByKey.keys - tgtByKey.keys) {
      diffs.add(ClassDifference(className, DifferenceType.METHOD_REMOVED, "${key.first}${key.second}"))
    }
    for (key in tgtByKey.keys - refByKey.keys) {
      diffs.add(ClassDifference(className, DifferenceType.METHOD_ADDED, "${key.first}${key.second}"))
      if (level >= AuditLevel.SEMANTIC) {
        val tgtMethod = tgtByKey[key]!!
        for (call in SuspiciousPatterns.scan(tgtMethod.instructions)) {
          diffs.add(ClassDifference(className, DifferenceType.SUSPICIOUS_INSTRUCTION,
            "${key.first}${key.second} [${call.category}] ${call.instruction}"))
        }
      }
    }

    if (level >= AuditLevel.BYTECODE) {
      for (key in refByKey.keys.intersect(tgtByKey.keys)) {
        val refMethod = refByKey[key]!!
        val tgtMethod = tgtByKey[key]!!
        if (refMethod.instructions != tgtMethod.instructions) {
          diffs.add(ClassDifference(className, DifferenceType.BYTECODE_CHANGED,
            "${key.first}${key.second}"))
          if (level >= AuditLevel.SEMANTIC) {
            for (call in SuspiciousPatterns.findNewSuspiciousInstructions(
              refMethod.instructions, tgtMethod.instructions
            )) {
              diffs.add(ClassDifference(className, DifferenceType.SUSPICIOUS_INSTRUCTION,
                "${key.first}${key.second} [${call.category}] ${call.instruction}"))
            }
          }
        }
      }
    }
  }

  private fun compareFields(
    className: String,
    refFields: List<ClassStructure.FieldInfo>,
    tgtFields: List<ClassStructure.FieldInfo>,
    diffs: MutableList<ClassDifference>,
  ) {
    val refSet = refFields.map { it.name to it.descriptor }.toSet()
    val tgtSet = tgtFields.map { it.name to it.descriptor }.toSet()

    for ((name, desc) in refSet - tgtSet) {
      diffs.add(ClassDifference(className, DifferenceType.FIELD_REMOVED, "$name $desc"))
    }
    for ((name, desc) in tgtSet - refSet) {
      diffs.add(ClassDifference(className, DifferenceType.FIELD_ADDED, "$name $desc"))
    }
  }
}

enum class DifferenceType(val text: String) {
  MISSING_IN_TARGET("missing in target"),
  EXTRA_IN_TARGET("extra in target"),
  SUPERCLASS_CHANGED("superclass changed"),
  INTERFACES_CHANGED("interfaces changed"),
  METHOD_REMOVED("method removed"),
  METHOD_ADDED("method added"),
  FIELD_REMOVED("field removed"),
  FIELD_ADDED("field added"),
  BYTECODE_CHANGED("bytecode changed"),
  SUSPICIOUS_INSTRUCTION("suspicious instruction"),
}

data class ClassDifference(
  val className: String,
  val type: DifferenceType,
  val symbol: String,
)

data class ComparisonReport(
  val referenceClassCount: Int,
  val targetClassCount: Int,
  val differences: List<ClassDifference>,
) {
  val isMatch: Boolean get() = differences.isEmpty()

  fun format(outputFormat: OutputFormat): String = when (outputFormat) {
    OutputFormat.TEXT -> text()
    OutputFormat.CSV -> csv()
    OutputFormat.MARKDOWN -> markdown()
  }

  fun text(): String = buildString {
    appendLine("Comparison Report")
    appendLine("=================")
    appendLine("Reference classes: $referenceClassCount")
    appendLine("Target classes:    $targetClassCount")
    appendLine("Differences:       ${differences.size}")
    if (differences.isNotEmpty()) {
      appendLine()
      for (diff in differences) {
        appendLine("  [${diff.type.text}] ${diff.className}: ${diff.symbol}")
      }
    } else {
      appendLine()
      appendLine("OK: All classes match structurally.")
    }
  }

  fun csv(): String = buildString {
    appendLine("\"class\",\"change\",\"symbol\"")
    for (diff in differences) {
      appendLine("${csvField(diff.className)},${csvField(diff.type.text)},${csvField(diff.symbol)}")
    }
  }

  fun markdown(): String = buildString {
    appendLine("## Comparison Report")
    appendLine()
    appendLine("| Metric | Value |")
    appendLine("|--------|-------|")
    appendLine("| Reference classes | $referenceClassCount |")
    appendLine("| Target classes | $targetClassCount |")
    appendLine("| Differences | ${differences.size} |")
    appendLine()
    if (differences.isNotEmpty()) {
      appendLine("| class | change | symbol |")
      appendLine("|-------|--------|--------|")
      for (diff in differences) {
        val sym = if (diff.symbol.isNotEmpty()) "`${mdEscape(diff.symbol)}`" else ""
        appendLine("| `${mdEscape(diff.className)}` | ${diff.type.text} | $sym |")
      }
    } else {
      appendLine("**OK:** All classes match structurally.")
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

enum class OutputFormat {
  TEXT, CSV, MARKDOWN;

  companion object {
    fun parse(value: String): OutputFormat = when (value.lowercase()) {
      "text" -> TEXT
      "csv" -> CSV
      "markdown", "md" -> MARKDOWN
      else -> throw IllegalArgumentException(
        "Unknown format: '$value'. Valid values: text, csv, markdown"
      )
    }
  }
}
