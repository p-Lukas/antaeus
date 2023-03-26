import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    base
    kotlin("jvm") version "1.7.21" apply false
}

allprojects {
    group = "io.pleo"
    version = "1.0"

    repositories {
        mavenCentral()
    }

    tasks.withType<KotlinCompile>().configureEach {
        kotlinOptions.jvmTarget = "17"
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}