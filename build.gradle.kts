plugins {
    kotlin("jvm") version "2.3.0"
    application
}

group = "com.github.steveloughran"
version = "1.0-SNAPSHOT"

application {
    mainClass.set("com.github.steveloughran.auditor.MainKt")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.ow2.asm:asm:9.7.1")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}