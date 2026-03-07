package com.github.steveloughran.auditor

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.Handle
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.util.Printer
import java.io.InputStream

/**
 * Parses .class file bytes into a [ClassStructure] using the ASM library.
 */
object ClassFileParser {

  fun parse(bytes: ByteArray, level: AuditLevel = AuditLevel.STRUCTURAL): ClassStructure {
    val reader = ClassReader(bytes)
    val visitor = StructureVisitor(level)
    val flags = if (level >= AuditLevel.BYTECODE) {
      ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES
    } else {
      ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES
    }
    reader.accept(visitor, flags)
    return visitor.build()
  }

  fun parse(
    input: InputStream,
    level: AuditLevel = AuditLevel.STRUCTURAL,
  ): ClassStructure = parse(input.readBytes(), level)

  private class StructureVisitor(
    private val level: AuditLevel,
  ) : ClassVisitor(Opcodes.ASM9) {
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
      return if (level >= AuditLevel.BYTECODE) {
        InstructionCollector(access, name, descriptor)
      } else {
        methods.add(ClassStructure.MethodInfo(name, descriptor, access))
        null
      }
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

    private inner class InstructionCollector(
      private val methodAccess: Int,
      private val methodName: String,
      private val methodDescriptor: String,
    ) : MethodVisitor(Opcodes.ASM9) {
      private val instructions = mutableListOf<String>()

      override fun visitInsn(opcode: Int) {
        instructions.add(opcodeName(opcode))
      }

      override fun visitIntInsn(opcode: Int, operand: Int) {
        instructions.add("${opcodeName(opcode)} $operand")
      }

      override fun visitVarInsn(opcode: Int, varIndex: Int) {
        instructions.add("${opcodeName(opcode)} $varIndex")
      }

      override fun visitTypeInsn(opcode: Int, type: String) {
        instructions.add("${opcodeName(opcode)} $type")
      }

      override fun visitFieldInsn(opcode: Int, owner: String, name: String, descriptor: String) {
        instructions.add("${opcodeName(opcode)} $owner.$name:$descriptor")
      }

      override fun visitMethodInsn(
        opcode: Int,
        owner: String,
        name: String,
        descriptor: String,
        isInterface: Boolean,
      ) {
        instructions.add("${opcodeName(opcode)} $owner.$name$descriptor")
      }

      override fun visitInvokeDynamicInsn(
        name: String,
        descriptor: String,
        bootstrapMethodHandle: Handle,
        vararg bootstrapMethodArguments: Any?,
      ) {
        instructions.add("INVOKEDYNAMIC $name$descriptor")
      }

      override fun visitJumpInsn(opcode: Int, label: Label) {
        instructions.add(opcodeName(opcode))
      }

      override fun visitLdcInsn(value: Any) {
        instructions.add("LDC ${value::class.simpleName}:$value")
      }

      override fun visitIincInsn(varIndex: Int, increment: Int) {
        instructions.add("IINC $varIndex $increment")
      }

      override fun visitTableSwitchInsn(min: Int, max: Int, dflt: Label, vararg labels: Label) {
        instructions.add("TABLESWITCH $min-$max")
      }

      override fun visitLookupSwitchInsn(dflt: Label, keys: IntArray, labels: Array<out Label>) {
        instructions.add("LOOKUPSWITCH ${keys.toList()}")
      }

      override fun visitMultiANewArrayInsn(descriptor: String, numDimensions: Int) {
        instructions.add("MULTIANEWARRAY $descriptor $numDimensions")
      }

      override fun visitEnd() {
        methods.add(ClassStructure.MethodInfo(
          methodName, methodDescriptor, methodAccess, instructions.toList()
        ))
      }

      private fun opcodeName(opcode: Int): String =
        if (opcode in Printer.OPCODES.indices) Printer.OPCODES[opcode] ?: "UNKNOWN_$opcode"
        else "UNKNOWN_$opcode"
    }
  }
}
