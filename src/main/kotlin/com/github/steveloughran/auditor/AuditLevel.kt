package com.github.steveloughran.auditor

enum class AuditLevel(val level: Int, val description: String) {
  STRUCTURAL(1, "structural comparison (methods, fields, hierarchy)"),
  BYTECODE(2, "bytecode comparison (instruction sequences)"),
  SEMANTIC(3, "semantic analysis (suspicious patterns)");

  companion object {
    fun parse(value: String): AuditLevel = when (value) {
      "1" -> STRUCTURAL
      "2" -> BYTECODE
      "3" -> SEMANTIC
      else -> throw IllegalArgumentException(
        "Unknown level: '$value'. Valid values: 1, 2, 3"
      )
    }
  }
}
