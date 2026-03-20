plugins {
    base
}

group = "com.insoftu"
version = "1.0-SNAPSHOT"

allprojects {
    repositories {
        mavenCentral()
    }
}

subprojects {

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}