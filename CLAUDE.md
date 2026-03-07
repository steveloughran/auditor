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
