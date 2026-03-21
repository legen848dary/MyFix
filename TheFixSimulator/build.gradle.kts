import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    id("java")
    id("jacoco")
    id("com.gradleup.shadow") version "8.3.6"
}

group = "com.llexsimulator"
version = "1.0-SNAPSHOT"

base {
    archivesName = "LLExSimulator"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

val aeronVersion     = "1.45.0"
val agronaVersion    = "1.22.0"
val artioVersion     = "0.154"
val sbeVersion       = "1.30.0"
val disruptorVersion = "4.0.0"
val vertxVersion     = "5.0.8"
val quickfixVersion  = "3.0.0"
val jacksonVersion   = "2.21.1"
val slf4jVersion     = "2.0.17"
val log4jVersion     = "2.25.3"
val junitVersion     = "6.0.3"

val sbeCodegen: Configuration by configurations.creating
val artioCodegen: Configuration by configurations.creating
val fixDictionarySpec: Configuration by configurations.creating

repositories {
    mavenCentral()
}

configure<JacocoPluginExtension> {
    toolVersion = "0.8.12"
}

dependencies {
    implementation("uk.co.real-logic:artio-codecs:$artioVersion")
    implementation("uk.co.real-logic:artio-core:$artioVersion")
    implementation("org.quickfixj:quickfixj-core:$quickfixVersion")
    implementation("org.quickfixj:quickfixj-messages-fix44:$quickfixVersion")
    artioCodegen("uk.co.real-logic:artio-codecs:$artioVersion")
    artioCodegen("org.agrona:agrona:$agronaVersion")
    artioCodegen("uk.co.real-logic:sbe-tool:1.32.1")
    fixDictionarySpec("org.quickfixj:quickfixj-messages-fix44:$quickfixVersion") {
        isTransitive = false
    }

    implementation("io.aeron:aeron-driver:$aeronVersion")
    implementation("io.aeron:aeron-client:$aeronVersion")
    implementation("io.aeron:aeron-archive:$aeronVersion")
    implementation("org.agrona:agrona:$agronaVersion")
    implementation("uk.co.real-logic:sbe-all:$sbeVersion")
    sbeCodegen("uk.co.real-logic:sbe-all:$sbeVersion")
    implementation("com.lmax:disruptor:$disruptorVersion")

    implementation(platform("io.vertx:vertx-stack-depchain:$vertxVersion"))
    implementation("io.vertx:vertx-core")
    implementation("io.vertx:vertx-web")

    implementation("org.hdrhistogram:HdrHistogram:2.2.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    implementation("org.apache.logging.log4j:log4j-core:$log4jVersion")
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:$log4jVersion")

    testImplementation(platform("org.junit:junit-bom:$junitVersion"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("io.vertx:vertx-junit5")
}

val sbeOutputDir = layout.buildDirectory.dir("generated/sources/sbe/main/java")
val artioFixSpecDir = layout.buildDirectory.dir("generated/resources/artio-fix")
val artioOutputDir = layout.buildDirectory.dir("generated/sources/artio/main/java")

val extractArtioFixDictionary by tasks.registering(Sync::class) {
    group = "build"
    description = "Extract the FIX44 XML dictionary used as the Artio codec-generation input"
    from(zipTree(fixDictionarySpec.singleFile)) {
        include("**/FIX44.xml")
        eachFile { path = name }
        includeEmptyDirs = false
    }
    into(artioFixSpecDir)
}

val generateArtioSources by tasks.registering(JavaExec::class) {
    group = "build"
    description = "Generate Artio FIX44 dictionary codecs from the FIX XML specification"
    dependsOn(extractArtioFixDictionary)
    classpath = artioCodegen
    mainClass.set("uk.co.real_logic.artio.dictionary.CodecGenerationTool")
    args(
        artioOutputDir.get().asFile.absolutePath,
        artioFixSpecDir.get().file("FIX44.xml").asFile.absolutePath
    )
    jvmArgs(
        "--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED"
    )
    doFirst {
        artioOutputDir.get().asFile.mkdirs()
    }
}

val generateSbeSources by tasks.registering(JavaExec::class) {
    group = "build"
    description = "Generate SBE flyweight codec classes from fix-messages.xml"
    classpath = sbeCodegen
    mainClass.set("uk.co.real_logic.sbe.SbeTool")
    args = listOf("src/main/resources/sbe/fix-messages.xml")
    jvmArgs(
        "--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED"
    )
    systemProperties(
        mapOf(
            "sbe.output.dir" to sbeOutputDir.get().asFile.absolutePath,
            "sbe.target.language" to "Java",
            "sbe.target.namespace" to "com.llexsimulator.sbe",
            "sbe.validation.stop.on.error" to "true",
            "sbe.validation.warnings.fatal" to "false",
            "sbe.java.generate.interfaces" to "true"
        )
    )
    doFirst {
        sbeOutputDir.get().asFile.mkdirs()
    }
}

sourceSets {
    main {
        java {
            srcDir(sbeOutputDir)
            srcDir(artioOutputDir)
        }
    }
}

tasks.jar {
    archiveBaseName.set("LLExSimulator")
    archiveClassifier.set("plain")
}

tasks.compileJava {
    dependsOn(generateSbeSources, generateArtioSources)
}

tasks.named<Jar>("shadowJar") {
    archiveBaseName.set("LLExSimulator")
    archiveClassifier.set("")
    manifest {
        attributes["Main-Class"] = "com.llexsimulator.Main"
    }
}

tasks.register<JavaExec>("run") {
    group = "application"
    description = "Run TheFixSimulator from the monorepo without the Gradle application plugin."
    dependsOn(tasks.named("classes"))
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.llexsimulator.Main")
    jvmArgs(
        "-Daeron.dir=/tmp/aeron-llexsim",
        "-Daeron.ipc.term.buffer.length=8388608",
        "-Dagrona.disable.bounds.checks=true",
        "--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED",
        "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
        "--add-opens=java.base/java.nio=ALL-UNNAMED",
        "--add-opens=java.base/java.lang=ALL-UNNAMED"
    )
}

tasks.test {
    useJUnitPlatform()
    jvmArgs(
        "--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED",
        "--add-opens", "java.base/sun.nio.ch=ALL-UNNAMED",
        "--add-opens", "java.base/java.nio=ALL-UNNAMED",
        "--add-opens", "java.base/java.lang=ALL-UNNAMED"
    )
    finalizedBy(tasks.named("jacocoTestReport"))
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}