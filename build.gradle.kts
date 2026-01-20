// 记得跟进最新版本
val ktorVersion = "3.3.3" // 检查更新：https://ktor.io/
val jsoupVersion = "1.22.1" // 检查更新：https://jsoup.org/
val ktSerJsonVersion = "1.9.0" // 检查更新：https://mvnrepository.com/artifact/org.jetbrains.kotlinx/kotlinx-serialization-json

plugins {
    kotlin("jvm") version "2.3.0" // 检查更新：https://kotlinlang.org/
    kotlin("plugin.serialization") version "2.3.0" // 检查更新：https://kotlinlang.org/
    id("com.gradleup.shadow") version "9.3.1" // 检查更新：https://plugins.gradle.org/plugin/com.gradleup.shadow
}

group = "lib.fetchmoodle"

// 年份/月份/修订
version = "2026.1.2"

repositories {
    mavenCentral()
    google()
}

dependencies {
    testImplementation(kotlin("test"))

    implementation("org.jsoup:jsoup:$jsoupVersion")
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${ktSerJsonVersion}")
}

tasks {
    test {
        useJUnitPlatform()
    }

    shadowJar {
        archiveClassifier.set("deps-inlined")
    }

    jar {
        archiveClassifier.set("deps-not-inlined")
    }

    register("packLibrary") {
        group = "build"
        description = "打包内联依赖以及未内联的库"

        dependsOn("shadowJar", "jar")

        doLast {
            println("内联依赖以及未内联的库打包完成，参见build/libs/")
        }
    }
}

kotlin {
    jvmToolchain(21)
}