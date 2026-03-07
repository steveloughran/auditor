package com.github.steveloughran.auditor

import org.objectweb.asm.Opcodes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClassFileParserTest {

    @Test
    fun `parse a known class from the classpath`() {
        // Parse String.class which is always available
        val bytes = String::class.java.getResourceAsStream("/${String::class.java.name.replace('.', '/')}.class")!!
            .readBytes()
        val structure = ClassFileParser.parse(bytes)

        assertEquals("java/lang/String", structure.className)
        assertEquals("java/lang/Object", structure.superClass)
        assertTrue(structure.interfaces.contains("java/io/Serializable"))
        assertTrue(structure.interfaces.contains("java/lang/Comparable"))
        assertTrue(structure.methods.isNotEmpty(), "String should have methods")
        assertTrue(structure.methods.any { it.name == "length" }, "String should have length()")
        assertTrue(structure.methods.any { it.name == "charAt" }, "String should have charAt()")
        assertTrue(structure.fields.isNotEmpty(), "String should have fields")
    }

    @Test
    fun `parse this test class itself`() {
        val bytes = this::class.java.getResourceAsStream(
            "/${this::class.java.name.replace('.', '/')}.class"
        )!!.readBytes()
        val structure = ClassFileParser.parse(bytes)

        assertEquals(
            "com/github/steveloughran/auditor/ClassFileParserTest",
            structure.className
        )
        assertEquals("java/lang/Object", structure.superClass)
        // Should find our test methods
        assertTrue(structure.methods.any { it.name == "parse a known class from the classpath" })
    }

    @Test
    fun `class version is populated`() {
        val bytes = Any::class.java.getResourceAsStream("/java/lang/Object.class")!!.readBytes()
        val structure = ClassFileParser.parse(bytes)
        assertTrue(structure.classVersion > 0, "Class version should be positive")
    }

    @Test
    fun `access flags are captured`() {
        val bytes = Any::class.java.getResourceAsStream("/java/lang/Object.class")!!.readBytes()
        val structure = ClassFileParser.parse(bytes)
        assertTrue(structure.access and Opcodes.ACC_PUBLIC != 0, "Object should be public")
    }
}
