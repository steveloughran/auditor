package com.github.steveloughran.auditor

/**
 * Detects suspicious bytecode patterns that may indicate tampering.
 * Used at audit level 3 to flag instructions in the target that
 * don't appear in the reference — potential injected backdoor code.
 */
object SuspiciousPatterns {

  data class SuspiciousCall(
    val category: String,
    val instruction: String,
  )

  private val SUSPICIOUS_PREFIXES = listOf(
    // Process execution
    "java/lang/Runtime.exec" to "process execution",
    "java/lang/ProcessBuilder" to "process execution",

    // Network access
    "java/net/Socket" to "network access",
    "java/net/ServerSocket" to "network access",
    "java/net/URL.openConnection" to "network access",
    "java/net/URL.openStream" to "network access",
    "java/net/HttpURLConnection" to "network access",
    "javax/net/ssl/HttpsURLConnection" to "network access",
    "java/net/DatagramSocket" to "network access",

    // Reflection
    "java/lang/Class.forName" to "reflection",
    "java/lang/Class.getMethod" to "reflection",
    "java/lang/Class.getDeclaredMethod" to "reflection",
    "java/lang/Class.getDeclaredField" to "reflection",
    "java/lang/reflect/Method.invoke" to "reflection",
    "java/lang/reflect/Field.set" to "reflection",
    "java/lang/reflect/Constructor.newInstance" to "reflection",

    // Class loading
    "java/lang/ClassLoader.loadClass" to "class loading",
    "java/lang/ClassLoader.defineClass" to "class loading",
    "java/net/URLClassLoader" to "class loading",

    // Native code
    "java/lang/System.load(" to "native code",
    "java/lang/System.loadLibrary" to "native code",
    "java/lang/Runtime.load(" to "native code",
    "java/lang/Runtime.loadLibrary" to "native code",

    // Cryptography (potential data exfiltration)
    "javax/crypto/Cipher" to "cryptography",
    "java/security/KeyPairGenerator" to "cryptography",

    // File system access to sensitive locations
    "java/lang/System.getenv" to "environment access",
    "java/lang/System.getProperty" to "environment access",

    // Scripting / code evaluation
    "javax/script/ScriptEngine.eval" to "script execution",

    // Serialization (deserialization attacks)
    "java/io/ObjectInputStream.readObject" to "deserialization",

    // Thread creation (hiding background activity)
    "java/lang/Thread.start" to "thread creation",
  )

  /**
   * Scan a list of instructions for suspicious calls.
   * Returns the suspicious calls found.
   */
  fun scan(instructions: List<String>): List<SuspiciousCall> {
    val results = mutableListOf<SuspiciousCall>()
    for (insn in instructions) {
      for ((prefix, category) in SUSPICIOUS_PREFIXES) {
        if (insn.contains(prefix)) {
          results.add(SuspiciousCall(category, insn))
          break
        }
      }
    }
    return results
  }

  /**
   * Find suspicious instructions in the target method that are NOT
   * present in the reference method. This identifies newly injected
   * suspicious behaviour.
   */
  fun findNewSuspiciousInstructions(
    refInstructions: List<String>,
    tgtInstructions: List<String>,
  ): List<SuspiciousCall> {
    val refSuspicious = scan(refInstructions).map { it.instruction }.toSet()
    return scan(tgtInstructions).filter { it.instruction !in refSuspicious }
  }
}
