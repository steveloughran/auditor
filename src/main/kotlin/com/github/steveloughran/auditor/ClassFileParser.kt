package com.github.steveloughran.auditor

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.io.InputStream

/**
 * Parses .class file bytes into a [ClassStructure] using the ASM library.
 */
object ClassFileParser {

  fun parse(bytes: ByteArray): ClassStructure {
    val reader = ClassReader(bytes)
    val visitor = StructureVisitor()
    reader.accept(visitor, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
    return visitor.build()
  }

  fun parse(input: InputStream): ClassStructure = parse(input.readBytes())

  private class StructureVisitor : ClassVisitor(Opcodes.ASM9) {
    private var className = ""
    private var superClass = ""
    private var interfaces = listOf<String>()
    private var access = 0
    private var classVersion = 0
    private val methods = mutableListOf<ClassStructure.MethodInfo>()
    private val fields = mutableListOf<ClassStructure.FieldInfo>()

    override fun visit(
      version: Int,
      access: Int,
      name: String,
      signature: String?,
      superName: String?,
      interfaces: Array<out String>?,
    ) {
      this.classVersion = version
      this.access = access
      this.className = name
      this.superClass = superName ?: ""
      this.interfaces = interfaces?.toList() ?: emptyList()
    }

    override fun visitMethod(
      access: Int,
      name: String,
      descriptor: String,
      signature: String?,
      exceptions: Array<out String>?,
    ): MethodVisitor? {
      methods.add(ClassStructure.MethodInfo(name, descriptor, access))
      return null
    }

    override fun visitField(
      access: Int,
      name: String,
      descriptor: String,
      signature: String?,
      value: Any?,
    ): FieldVisitor? {
      fields.add(ClassStructure.FieldInfo(name, descriptor, access))
      return null
    }

    fun build(): ClassStructure = ClassStructure(
      className = className,
      superClass = superClass,
      interfaces = interfaces,
      access = access,
      methods = methods.toList(),
      fields = fields.toList(),
      classVersion = classVersion,
    )
  }
}
