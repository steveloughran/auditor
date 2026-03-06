package com.github.steveloughran.auditor

/**
 * Structural representation of a compiled .class file.
 * Captures the metadata needed for comparison without requiring
 * byte-identical class files.
 */
data class ClassStructure(
    val className: String,
    val superClass: String,
    val interfaces: List<String>,
    val access: Int,
    val methods: List<MethodInfo>,
    val fields: List<FieldInfo>,
    val classVersion: Int,
) {
    data class MethodInfo(
        val name: String,
        val descriptor: String,
        val access: Int,
    )

    data class FieldInfo(
        val name: String,
        val descriptor: String,
        val access: Int,
    )
}
