package com.github.steveloughran.auditor

import org.assertj.core.api.Assertions.assertThat
import kotlin.test.Test

class SuspiciousPatternsTest {

  @Test
  fun `detect Runtime exec`() {
    val instructions = listOf(
      "ALOAD 0",
      "INVOKEVIRTUAL java/lang/Runtime.exec(Ljava/lang/String;)Ljava/lang/Process;",
      "ARETURN",
    )
    val results = SuspiciousPatterns.scan(instructions)
    assertThat(results).hasSize(1)
    assertThat(results[0].category).isEqualTo("process execution")
  }

  @Test
  fun `detect network socket`() {
    val instructions = listOf(
      "NEW java/net/Socket",
      "DUP",
      "LDC String:evil.example.com",
      "BIPUSH 8080",
      "INVOKESPECIAL java/net/Socket.<init>(Ljava/lang/String;I)V",
    )
    val results = SuspiciousPatterns.scan(instructions)
    assertThat(results).isNotEmpty
    assertThat(results).anyMatch { it.category == "network access" }
  }

  @Test
  fun `detect reflection`() {
    val instructions = listOf(
      "LDC String:com.evil.Backdoor",
      "INVOKESTATIC java/lang/Class.forName(Ljava/lang/String;)Ljava/lang/Class;",
    )
    val results = SuspiciousPatterns.scan(instructions)
    assertThat(results).hasSize(1)
    assertThat(results[0].category).isEqualTo("reflection")
  }

  @Test
  fun `detect class loading`() {
    val instructions = listOf(
      "INVOKEVIRTUAL java/lang/ClassLoader.loadClass(Ljava/lang/String;)Ljava/lang/Class;",
    )
    val results = SuspiciousPatterns.scan(instructions)
    assertThat(results).hasSize(1)
    assertThat(results[0].category).isEqualTo("class loading")
  }

  @Test
  fun `detect native code loading`() {
    val instructions = listOf(
      "INVOKESTATIC java/lang/System.loadLibrary(Ljava/lang/String;)V",
    )
    val results = SuspiciousPatterns.scan(instructions)
    assertThat(results).hasSize(1)
    assertThat(results[0].category).isEqualTo("native code")
  }

  @Test
  fun `detect thread creation`() {
    val instructions = listOf(
      "INVOKEVIRTUAL java/lang/Thread.start()V",
    )
    val results = SuspiciousPatterns.scan(instructions)
    assertThat(results).hasSize(1)
    assertThat(results[0].category).isEqualTo("thread creation")
  }

  @Test
  fun `no false positives on normal code`() {
    val instructions = listOf(
      "ALOAD 0",
      "GETFIELD com/example/Foo.name:Ljava/lang/String;",
      "INVOKEVIRTUAL java/lang/String.length()I",
      "IRETURN",
    )
    val results = SuspiciousPatterns.scan(instructions)
    assertThat(results).isEmpty()
  }

  @Test
  fun `findNewSuspiciousInstructions only flags new calls`() {
    val ref = listOf(
      "INVOKEVIRTUAL java/lang/Thread.start()V",
      "RETURN",
    )
    val tgt = listOf(
      "INVOKEVIRTUAL java/lang/Thread.start()V",
      "INVOKEVIRTUAL java/lang/Runtime.exec(Ljava/lang/String;)Ljava/lang/Process;",
      "RETURN",
    )
    val results = SuspiciousPatterns.findNewSuspiciousInstructions(ref, tgt)
    assertThat(results).hasSize(1)
    assertThat(results[0].category).isEqualTo("process execution")
  }

  @Test
  fun `findNewSuspiciousInstructions empty when no new suspicious calls`() {
    val ref = listOf(
      "INVOKEVIRTUAL java/lang/Thread.start()V",
      "RETURN",
    )
    val tgt = listOf(
      "INVOKEVIRTUAL java/lang/Thread.start()V",
      "ALOAD 0",
      "RETURN",
    )
    val results = SuspiciousPatterns.findNewSuspiciousInstructions(ref, tgt)
    assertThat(results).isEmpty()
  }
}
