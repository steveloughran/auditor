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

    val refMd5 = JarAnalyzer.md5(referencePath)
    val tgtMd5 = JarAnalyzer.md5(targetPath)
    println("Reference: $referencePath (MD5: $refMd5)")
    println("Target:    $targetPath (MD5: $tgtMd5)")

    if (refMd5 == tgtMd5) {
        println()
        println("JARs are byte-identical (MD5 match). No further analysis needed.")
        exitProcess(0)
    }

    println()
    println("MD5 checksums differ. Performing structural comparison...")
    println()

    val reference = JarAnalyzer.analyze(referencePath)
    val target = JarAnalyzer.analyze(targetPath)

    val report = ClassComparator.compare(reference, target)
    print(report.summary())
    if (!report.isMatch) {
        print(report.csv())
    }

    exitProcess(if (report.isMatch) 0 else 1)
}
