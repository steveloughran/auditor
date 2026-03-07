package com.github.steveloughran.auditor

import org.assertj.core.api.Assertions.assertThat
import kotlin.test.Test

class ClassComparatorTest {

    private fun structure(
        name: String = "com/example/Foo",
        superClass: String = "java/lang/Object",
        interfaces: List<String> = emptyList(),
        methods: List<ClassStructure.MethodInfo> = emptyList(),
        fields: List<ClassStructure.FieldInfo> = emptyList(),
    ) = ClassStructure(
        className = name,
        superClass = superClass,
        interfaces = interfaces,
        access = 0,
        methods = methods,
        fields = fields,
        classVersion = 61,
    )

    @Test
    fun `identical structures match`() {
        val s = structure(methods = listOf(
            ClassStructure.MethodInfo("foo", "()V", 1),
            ClassStructure.MethodInfo("bar", "(I)I", 1),
        ))
        val report = ClassComparator.compare(mapOf(s.className to s), mapOf(s.className to s))
        assertThat(report.isMatch).isTrue()
        assertThat(report.differences).isEmpty()
    }

    @Test
    fun `detect missing class in target`() {
        val s = structure()
        val report = ClassComparator.compare(
            mapOf(s.className to s),
            emptyMap(),
        )
        assertThat(report.isMatch).isFalse()
        assertThat(report.differences).hasSize(1)
        assertThat(report.differences[0].type).isEqualTo(DifferenceType.MISSING_IN_TARGET)
    }

    @Test
    fun `detect extra class in target`() {
        val s = structure()
        val report = ClassComparator.compare(
            emptyMap(),
            mapOf(s.className to s),
        )
        assertThat(report.isMatch).isFalse()
        assertThat(report.differences).hasSize(1)
        assertThat(report.differences[0].type).isEqualTo(DifferenceType.EXTRA_IN_TARGET)
    }

    @Test
    fun `detect superclass change`() {
        val ref = structure(superClass = "java/lang/Object")
        val tgt = structure(superClass = "java/lang/Throwable")
        val report = ClassComparator.compare(
            mapOf(ref.className to ref),
            mapOf(tgt.className to tgt),
        )
        assertThat(report.isMatch).isFalse()
        assertThat(report.differences)
            .anyMatch { it.type == DifferenceType.SUPERCLASS_CHANGED }
    }

    @Test
    fun `detect interface change`() {
        val ref = structure(interfaces = listOf("java/io/Serializable"))
        val tgt = structure(interfaces = listOf("java/io/Serializable", "java/lang/Cloneable"))
        val report = ClassComparator.compare(
            mapOf(ref.className to ref),
            mapOf(tgt.className to tgt),
        )
        assertThat(report.isMatch).isFalse()
        assertThat(report.differences)
            .anyMatch { it.type == DifferenceType.INTERFACES_CHANGED }
    }

    @Test
    fun `detect added method`() {
        val ref = structure(methods = listOf(
            ClassStructure.MethodInfo("foo", "()V", 1),
        ))
        val tgt = structure(methods = listOf(
            ClassStructure.MethodInfo("foo", "()V", 1),
            ClassStructure.MethodInfo("evil", "()V", 1),
        ))
        val report = ClassComparator.compare(
            mapOf(ref.className to ref),
            mapOf(tgt.className to tgt),
        )
        assertThat(report.isMatch).isFalse()
        assertThat(report.differences)
            .anyMatch { it.type == DifferenceType.METHOD_ADDED }
        assertThat(report.differences)
            .anyMatch { it.symbol.contains("evil") }
    }

    @Test
    fun `detect removed method`() {
        val ref = structure(methods = listOf(
            ClassStructure.MethodInfo("foo", "()V", 1),
            ClassStructure.MethodInfo("bar", "()V", 1),
        ))
        val tgt = structure(methods = listOf(
            ClassStructure.MethodInfo("foo", "()V", 1),
        ))
        val report = ClassComparator.compare(
            mapOf(ref.className to ref),
            mapOf(tgt.className to tgt),
        )
        assertThat(report.isMatch).isFalse()
        assertThat(report.differences)
            .anyMatch { it.type == DifferenceType.METHOD_REMOVED }
    }

    @Test
    fun `detect added field`() {
        val ref = structure(fields = emptyList())
        val tgt = structure(fields = listOf(
            ClassStructure.FieldInfo("backdoor", "Ljava/lang/String;", 1),
        ))
        val report = ClassComparator.compare(
            mapOf(ref.className to ref),
            mapOf(tgt.className to tgt),
        )
        assertThat(report.isMatch).isFalse()
        assertThat(report.differences)
            .anyMatch { it.type == DifferenceType.FIELD_ADDED }
    }

    @Test
    fun `detect removed field`() {
        val ref = structure(fields = listOf(
            ClassStructure.FieldInfo("data", "I", 1),
        ))
        val tgt = structure(fields = emptyList())
        val report = ClassComparator.compare(
            mapOf(ref.className to ref),
            mapOf(tgt.className to tgt),
        )
        assertThat(report.isMatch).isFalse()
        assertThat(report.differences)
            .anyMatch { it.type == DifferenceType.FIELD_REMOVED }
    }

    @Test
    fun `report summary for matching JARs`() {
        val s = structure()
        val report = ClassComparator.compare(mapOf(s.className to s), mapOf(s.className to s))
        assertThat(report.summary()).contains("OK", "Differences:       0")
        assertThat(report.csv()).isEqualTo("\"class\",\"change\",\"symbol\"\n")
    }

    @Test
    fun `csv output has header and rows`() {
        val ref = structure(methods = listOf(
            ClassStructure.MethodInfo("foo", "()V", 1),
        ))
        val tgt = structure(methods = listOf(
            ClassStructure.MethodInfo("foo", "()V", 1),
            ClassStructure.MethodInfo("evil", "()V", 1),
        ))
        val report = ClassComparator.compare(
            mapOf(ref.className to ref),
            mapOf(tgt.className to tgt),
        )
        val csv = report.csv()
        val lines = csv.trimEnd().lines()
        assertThat(lines[0]).isEqualTo("\"class\",\"change\",\"symbol\"")
        assertThat(lines).hasSize(2)
        assertThat(lines[1]).contains("com/example/Foo")
        assertThat(lines[1]).contains("METHOD_ADDED")
        assertThat(lines[1]).contains("evil")
    }

    @Test
    fun `csv output for mismatched JARs`() {
        val report = ClassComparator.compare(
            mapOf("A" to structure(name = "A")),
            mapOf("B" to structure(name = "B")),
        )
        val csv = report.csv()
        val lines = csv.trimEnd().lines()
        assertThat(lines).hasSize(3) // header + 2 differences
        assertThat(csv).contains("MISSING_IN_TARGET")
        assertThat(csv).contains("EXTRA_IN_TARGET")
    }
}
