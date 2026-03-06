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
    ): ComparisonReport {
        val differences = mutableListOf<ClassDifference>()

        val onlyInReference = reference.keys - target.keys
        val onlyInTarget = target.keys - reference.keys
        val common = reference.keys.intersect(target.keys)

        for (name in onlyInReference) {
            differences.add(ClassDifference(name, DifferenceType.MISSING_IN_TARGET, "Class only in reference"))
        }
        for (name in onlyInTarget) {
            differences.add(ClassDifference(name, DifferenceType.EXTRA_IN_TARGET, "Class only in target"))
        }
        for (name in common.sorted()) {
            differences.addAll(compareClasses(name, reference[name]!!, target[name]!!))
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
    ): List<ClassDifference> {
        val diffs = mutableListOf<ClassDifference>()

        if (ref.superClass != tgt.superClass) {
            diffs.add(ClassDifference(name, DifferenceType.SUPERCLASS_CHANGED,
                "superclass: ${ref.superClass} -> ${tgt.superClass}"))
        }
        if (ref.interfaces.sorted() != tgt.interfaces.sorted()) {
            diffs.add(ClassDifference(name, DifferenceType.INTERFACES_CHANGED,
                "interfaces: ${ref.interfaces} -> ${tgt.interfaces}"))
        }

        compareMethods(name, ref.methods, tgt.methods, diffs)
        compareFields(name, ref.fields, tgt.fields, diffs)

        return diffs
    }

    private fun compareMethods(
        className: String,
        refMethods: List<ClassStructure.MethodInfo>,
        tgtMethods: List<ClassStructure.MethodInfo>,
        diffs: MutableList<ClassDifference>,
    ) {
        val refSet = refMethods.map { it.name to it.descriptor }.toSet()
        val tgtSet = tgtMethods.map { it.name to it.descriptor }.toSet()

        for ((name, desc) in refSet - tgtSet) {
            diffs.add(ClassDifference(className, DifferenceType.METHOD_REMOVED,
                "method removed: $name$desc"))
        }
        for ((name, desc) in tgtSet - refSet) {
            diffs.add(ClassDifference(className, DifferenceType.METHOD_ADDED,
                "method added: $name$desc"))
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
            diffs.add(ClassDifference(className, DifferenceType.FIELD_REMOVED,
                "field removed: $name $desc"))
        }
        for ((name, desc) in tgtSet - refSet) {
            diffs.add(ClassDifference(className, DifferenceType.FIELD_ADDED,
                "field added: $name $desc"))
        }
    }
}

enum class DifferenceType {
    MISSING_IN_TARGET,
    EXTRA_IN_TARGET,
    SUPERCLASS_CHANGED,
    INTERFACES_CHANGED,
    METHOD_REMOVED,
    METHOD_ADDED,
    FIELD_REMOVED,
    FIELD_ADDED,
}

data class ClassDifference(
    val className: String,
    val type: DifferenceType,
    val detail: String,
)

data class ComparisonReport(
    val referenceClassCount: Int,
    val targetClassCount: Int,
    val differences: List<ClassDifference>,
) {
    val isMatch: Boolean get() = differences.isEmpty()

    fun summary(): String = buildString {
        appendLine("Comparison Report")
        appendLine("=================")
        appendLine("Reference classes: $referenceClassCount")
        appendLine("Target classes:    $targetClassCount")
        appendLine("Differences:       ${differences.size}")
        if (differences.isNotEmpty()) {
            appendLine()
            for (diff in differences) {
                appendLine("  [${diff.type}] ${diff.className}: ${diff.detail}")
            }
        } else {
            appendLine()
            appendLine("OK: All classes match structurally.")
        }
    }
}
