package com.github.steveloughran.auditor

import org.assertj.core.api.Assertions.assertThat
import org.objectweb.asm.Opcodes
import kotlin.test.Test

class ClassFileParserTest {

  @Test
  fun `parse a known class from the classpath`() {
    val bytes = String::class.java.getResourceAsStream("/${String::class.java.name.replace('.', '/')}.class")!!
      .readBytes()
    val structure = ClassFileParser.parse(bytes)

    assertThat(structure.className).isEqualTo("java/lang/String")
    assertThat(structure.superClass).isEqualTo("java/lang/Object")
    assertThat(structure.interfaces).contains("java/io/Serializable", "java/lang/Comparable")
    assertThat(structure.methods).isNotEmpty
    assertThat(structure.methods.map { it.name }).contains("length", "charAt")
    assertThat(structure.fields).isNotEmpty
  }

  @Test
  fun `parse this test class itself`() {
    val bytes = this::class.java.getResourceAsStream(
      "/${this::class.java.name.replace('.', '/')}.class"
    )!!.readBytes()
    val structure = ClassFileParser.parse(bytes)

    assertThat(structure.className)
      .isEqualTo("com/github/steveloughran/auditor/ClassFileParserTest")
    assertThat(structure.superClass).isEqualTo("java/lang/Object")
    assertThat(structure.methods.map { it.name })
      .contains("parse a known class from the classpath")
  }

  @Test
  fun `class version is populated`() {
    val bytes = Any::class.java.getResourceAsStream("/java/lang/Object.class")!!.readBytes()
    val structure = ClassFileParser.parse(bytes)
    assertThat(structure.classVersion).isGreaterThan(0)
  }

  @Test
  fun `access flags are captured`() {
    val bytes = Any::class.java.getResourceAsStream("/java/lang/Object.class")!!.readBytes()
    val structure = ClassFileParser.parse(bytes)
    assertThat(structure.access and Opcodes.ACC_PUBLIC).isNotEqualTo(0)
  }
}
