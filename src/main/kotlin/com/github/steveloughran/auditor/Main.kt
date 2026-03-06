package com.github.steveloughran.auditor

import java.nio.file.Path
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if (args.size != 2) {
        System.err.println("Usage: auditor <reference.jar> <target.jar>")
        System.err.println()
        System.err.println("  reference.jar  JAR compiled from trusted source")
        System.err.println("  target.jar     JAR to audit (e.g. downloaded binary)")
        exitProcess(1)
    }

    val referencePath = Path.of(args[0])
    val targetPath = Path.of(args[1])

    if (!referencePath.toFile().exists()) {
        System.err.println("Reference JAR not found: $referencePath")
        exitProcess(1)
    }
    if (!targetPath.toFile().exists()) {
        System.err.println("Target JAR not found: $targetPath")
        exitProcess(1)
    }

    println("Analyzing reference: $referencePath")
    val reference = JarAnalyzer.analyze(referencePath)

    println("Analyzing target:    $targetPath")
    val target = JarAnalyzer.analyze(targetPath)

    val report = ClassComparator.compare(reference, target)
    println()
    print(report.summary())

    exitProcess(if (report.isMatch) 0 else 1)
}
