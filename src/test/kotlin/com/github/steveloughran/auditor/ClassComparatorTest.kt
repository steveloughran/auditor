package com.github.steveloughran.auditor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
        assertTrue(report.isMatch)
        assertEquals(0, report.differences.size)
    }

    @Test
    fun `detect missing class in target`() {
        val s = structure()
        val report = ClassComparator.compare(
            mapOf(s.className to s),
            emptyMap(),
        )
        assertFalse(report.isMatch)
        assertEquals(1, report.differences.size)
        assertEquals(DifferenceType.MISSING_IN_TARGET, report.differences[0].type)
    }

    @Test
    fun `detect extra class in target`() {
        val s = structure()
        val report = ClassComparator.compare(
            emptyMap(),
            mapOf(s.className to s),
        )
        assertFalse(report.isMatch)
        assertEquals(1, report.differences.size)
        assertEquals(DifferenceType.EXTRA_IN_TARGET, report.differences[0].type)
    }

    @Test
    fun `detect superclass change`() {
        val ref = structure(superClass = "java/lang/Object")
        val tgt = structure(superClass = "java/lang/Throwable")
        val report = ClassComparator.compare(
            mapOf(ref.className to ref),
            mapOf(tgt.className to tgt),
        )
        assertFalse(report.isMatch)
        assertTrue(report.differences.any { it.type == DifferenceType.SUPERCLASS_CHANGED })
    }

    @Test
    fun `detect interface change`() {
        val ref = structure(interfaces = listOf("java/io/Serializable"))
        val tgt = structure(interfaces = listOf("java/io/Serializable", "java/lang/Cloneable"))
        val report = ClassComparator.compare(
            mapOf(ref.className to ref),
            mapOf(tgt.className to tgt),
        )
        assertFalse(report.isMatch)
        assertTrue(report.differences.any { it.type == DifferenceType.INTERFACES_CHANGED })
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
        assertFalse(report.isMatch)
        assertTrue(report.differences.any { it.type == DifferenceType.METHOD_ADDED })
        assertTrue(report.differences.any { it.detail.contains("evil") })
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
        assertFalse(report.isMatch)
        assertTrue(report.differences.any { it.type == DifferenceType.METHOD_REMOVED })
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
        assertFalse(report.isMatch)
        assertTrue(report.differences.any { it.type == DifferenceType.FIELD_ADDED })
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
        assertFalse(report.isMatch)
        assertTrue(report.differences.any { it.type == DifferenceType.FIELD_REMOVED })
    }

    @Test
    fun `report summary for matching JARs`() {
        val s = structure()
        val report = ClassComparator.compare(mapOf(s.className to s), mapOf(s.className to s))
        val summary = report.summary()
        assertTrue(summary.contains("OK"))
        assertTrue(summary.contains("Differences:       0"))
    }

    @Test
    fun `report summary for mismatched JARs`() {
        val report = ClassComparator.compare(
            mapOf("A" to structure(name = "A")),
            mapOf("B" to structure(name = "B")),
        )
        val summary = report.summary()
        assertTrue(summary.contains("MISSING_IN_TARGET"))
        assertTrue(summary.contains("EXTRA_IN_TARGET"))
    }
}
