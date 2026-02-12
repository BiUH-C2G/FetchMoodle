import org.gradle.api.tasks.testing.Test

// 记得跟进最新版本
val ktorVersion = "3.3.3" // 检查更新：https://ktor.io/
val ksoupVersion = "0.2.5" // 检查更新：https://jsoup.org/
val ktSerJsonVersion = "1.9.0" // 检查更新：https://mvnrepository.com/artifact/org.jetbrains.kotlinx/kotlinx-serialization-json

plugins {
    kotlin("multiplatform") version "2.3.0" // 检查更新：https://kotlinlang.org/
    kotlin("plugin.serialization") version "2.3.0" // 检查更新：https://kotlinlang.org/
    id("com.android.library") version "8.10.1" // 检查更新：https://developer.android.com/build/releases/gradle-plugin
    id("maven-publish")
}

group = "lib.fetchmoodle"

// 年份/月份/修订
version = "2026.2.2"

publishing {
    repositories {
        maven {
            name = "ghPages"
            url = uri(layout.buildDirectory.dir("gh-pages-repo"))
        }
    }
}

repositories {
    mavenCentral()
    google()
}

kotlin {
    androidTarget()
    jvm()
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    jvmToolchain(21)

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("com.fleeksoft.ksoup:ksoup:$ksoupVersion")
                implementation("io.ktor:ktor-client-core:$ktorVersion")
                implementation ("io.ktor:ktor-client-cio:${ktorVersion}")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$ktSerJsonVersion")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit5"))
            }
        }

        val iosX64Main by getting
        val iosArm64Main by getting
        val iosSimulatorArm64Main by getting

        val iosMain by creating {
            dependsOn(commonMain)
            iosX64Main.dependsOn(this)
            iosArm64Main.dependsOn(this)
            iosSimulatorArm64Main.dependsOn(this)
        }
    }

}

android {
    namespace = group.toString()
    compileSdk = 35

    defaultConfig { minSdk = 24 }
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform {
        excludeTags("integration")
    }
}

tasks.register<Test>("jvmIntegrationTest") {
    group = "verification"
    description = "Runs JVM integration tests tagged with @Tag(\"integration\")."

    dependsOn("jvmTestClasses")

    val jvmTestTask = tasks.named<Test>("jvmTest").get()
    testClassesDirs = jvmTestTask.testClassesDirs
    classpath = jvmTestTask.classpath

    useJUnitPlatform {
        includeTags("integration")
    }

    shouldRunAfter("jvmTest")
}