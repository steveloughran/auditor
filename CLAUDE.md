# auditor

A security auditing tool to verify that compiled `.class` files in JAR artifacts match the original source code. Targets supply-chain attacks where a release manager could publish subtly tampered binaries.

## Project overview

- **Language:** Kotlin (JVM)
- **Build:** Gradle 9.0.0 with Kotlin DSL
- **JDK target:** 17
- **Kotlin version:** 2.3.0
- **Testing:** JUnit 5 (JUnit Platform)
- **License:** Apache 2.0
- **Group:** `com.github.steveloughran`

## Architecture

The tool compares `.class` files from a distributed JAR against those compiled from the original source. Because `javac` output is not guaranteed to be byte-identical across compilers/versions/platforms, the comparison is structural rather than binary:

- Compare method count, names, and signatures
- Compare field count, names, and types
- Compare class hierarchy (superclass, interfaces)
- Flag any discrepancies as potential tampering indicators

## Development guidelines

- Build with `./gradlew uberJar` (when user says "build", always create the uber jar)
- Run tests with `./gradlew test`
- Follow Kotlin official code style (`kotlin.code.style=official`)
- Source goes in `src/main/kotlin/`, tests in `src/test/kotlin/`
- Use the ASM library or parsing for `.class` file analysis
- Keep the tool usable as both a CLI and a library
- classes go in inder the package com.github.steveloughran.auditor
- use assertj for test assertions

## Key concepts

- **Structural comparison:** Compare class metadata (methods, fields, signatures) rather than raw bytes
- **JAR analysis:** Extract and enumerate `.class` files from JAR/ZIP archives
- **Source correlation:** Map `.class` files back to source files accounting for inner classes, anonymous classes, etc.
- **Threat model:** A malicious release manager with commit access who builds and signs tampered artifacts

Algorithm

- when first comparing two jars, perform an md5 checksum on each and report if they are identical -and skip the validation
- scan each class looking for changes in imports, methods or symbols

make the output csv formatted with headers "class", "change", "symbol", with contents as appropriate 

whenever I say "build", create the uber jar

## Q2: the internals

Claude, would it be possible to compare java assembly codes to look for different code sequences within a method, particularly if classes were built with debugging        
 symbols                                                                                                                                                           
                                                                                                                                                                        
     Yes, absolutely. ASM already gives us everything we need — we're currently using SKIP_CODE which discards the method bodies. Removing that flag would let us visit 
     the actual bytecode instructions.                                                                                                                                
                                                                                                                                                                        
     What's feasible
                                                                                                                                                                        
     Instruction sequence comparison — ASM's MethodVisitor receives every bytecode instruction (visitInsn, visitMethodInsn, visitFieldInsn, visitLdcInsn, etc.). We   
     could capture the opcode sequence for each method and compare them between reference and target.
    
     Debug symbol differences — These are the main source of "legitimate" differences between otherwise identical compilations:
     - Line number tables (visitLineNumber) — map bytecode offsets to source lines. Different compilers or compiler flags (-g vs -g:none) produce different tables, or
     omit them entirely.
       - Local variable tables (visitLocalVariable) — variable names, types, and scope ranges. Again, only present with debug info.
       - Source file attribute — visitSource gives the filename.
    
     Practical approach for auditing:
    
     1. Compare instructions, ignore debug metadata — Strip line numbers and local variable tables before comparing. This way, a release built with -g:none would still
     match one built with -g:lines,vars,source as long as the actual code is the same.
        2. Normalize constant pool references — Instructions reference constants by index. ASM abstracts this away (you get the actual string/type/value), so comparisons
        would work across different constant pool orderings.
        3. Handle compiler-generated differences — Even with the same source, different javac versions may:
        - Reorder switch table entries
        - Use different string concatenation strategies (StringBuilder vs invokedynamic)
        - Generate different bridge methods for generics
        - Use different local variable slot assignments
    
     A tiered approach makes sense:
     - Level 1 (current): structural — method/field signatures, class hierarchy
       - Level 2: bytecode — compare instruction sequences per method (ignoring debug info)
       - Level 3: semantic — flag specific suspicious patterns (e.g., new network calls, reflection, Runtime.exec(), class loading)
    
     Level 3 would be the most valuable for the threat model — a tampered release would likely inject code that calls out to external services or executes commands. We
     could scan for specific opcodes/method references that are suspicious when they appear in the target but not the reference.
    
