plugins {
  kotlin("jvm") version "2.3.0"
  application
}

group = "com.github.steveloughran"
version = "1.1-SNAPSHOT"

application {
  mainClass.set("com.github.steveloughran.auditor.MainKt")
}

repositories {
  mavenCentral()
}

dependencies {
  implementation("org.ow2.asm:asm:9.7.1")
  implementation("org.ow2.asm:asm-util:9.7.1")
  testImplementation(kotlin("test"))
  testImplementation("org.assertj:assertj-core:3.27.3")
}

kotlin {
  jvmToolchain(17)
}

tasks.test {
  useJUnitPlatform()
}

tasks.register<Jar>("uberJar") {
  archiveClassifier.set("all")
  duplicatesStrategy = DuplicatesStrategy.EXCLUDE
  manifest {
    attributes["Main-Class"] = "com.github.steveloughran.auditor.MainKt"
  }
  from(sourceSets.main.get().output)
  dependsOn(configurations.runtimeClasspath)
  from({
    configurations.runtimeClasspath.get()
      .filter { it.name.endsWith(".jar") }
      .map { zipTree(it) }
  })
}

tasks.register<Exec>("githubRelease") {
  dependsOn("uberJar")
  group = "publishing"
  description = "Create a GitHub release and upload the uber JAR"

  val tag = "v${project.version}"
  val uberJar = tasks.named<Jar>("uberJar").get().archiveFile.get().asFile
  val releaseNotes = project.findProperty("releaseNotes")?.toString() ?: "Release $tag"

  commandLine(
    "gh", "release", "create", tag,
    uberJar.absolutePath,
    "--title", tag,
    "--notes", releaseNotes,
  )
}