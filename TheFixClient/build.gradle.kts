plugins {
    application
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    implementation(project(":TheFixSimulator"))

    implementation(platform("io.vertx:vertx-stack-depchain:5.0.8"))
    implementation("io.vertx:vertx-core")
    implementation("io.vertx:vertx-web")
    implementation("org.slf4j:slf4j-api:2.0.17")
    implementation("org.apache.logging.log4j:log4j-core:2.25.3")
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:2.25.3")
    implementation("com.h2database:h2:2.2.224")
    implementation("org.quickfixj:quickfixj-core:3.0.0")
    implementation("org.quickfixj:quickfixj-messages-fix42:3.0.0")
    implementation("org.quickfixj:quickfixj-messages-fix44:3.0.0")
    implementation("org.quickfixj:quickfixj-messages-fix50:3.0.0")
    implementation("org.quickfixj:quickfixj-messages-fix50sp2:3.0.0")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass = "com.insoftu.thefix.client.TheFixClientApplication"
    applicationDefaultJvmArgs = listOf(
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
}

