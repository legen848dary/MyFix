plugins {
    application
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    implementation(platform("io.vertx:vertx-stack-depchain:5.0.8"))
    implementation("io.vertx:vertx-core")
    implementation("io.vertx:vertx-web")
    implementation("org.slf4j:slf4j-api:2.0.17")
    implementation("org.apache.logging.log4j:log4j-core:2.25.3")
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:2.25.3")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass = "com.insoftu.thefix.client.TheFixClientApplication"
}

