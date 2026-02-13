import com.codingfeline.buildkonfig.compiler.FieldSpec
import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotest)
    alias(libs.plugins.buildkonfig)
    id("maven-publish")
}

group = "lib.fetchmoodle"

// 年份/月份/修订
version = "2026.2.3"

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}

fun localProperty(name: String): String = localProperties.getProperty(name, "").trim()

publishing {
    publications.withType<MavenPublication> {
        artifactId = "core"
    }

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
                implementation(libs.ksoup)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.cio)
                implementation(libs.kotlinx.serialization.json)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(libs.kotest.framework.engine)
                implementation(libs.kotest.assertions.core)
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(libs.kotest.runner.junit5)
            }
        }

        val iosArm64Main by getting
        val iosSimulatorArm64Main by getting

        val iosMain by creating {
            dependsOn(commonMain)
            iosArm64Main.dependsOn(this)
            iosSimulatorArm64Main.dependsOn(this)
        }
    }
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
    filter { isFailOnNoMatchingTests = false }
}

android {
    namespace = group.toString()
    compileSdk = 36

    defaultConfig { minSdk = 24 }
}

buildkonfig {
    packageName = group.toString()

    defaultConfigs {
        buildConfigField(FieldSpec.Type.STRING, "MOODLE_TEST_URL", localProperty("moodle.test.url"))
        buildConfigField(FieldSpec.Type.STRING, "MOODLE_TEST_USERNAME", localProperty("moodle.test.username"))
        buildConfigField(FieldSpec.Type.STRING, "MOODLE_TEST_PASSWORD", localProperty("moodle.test.password"))
    }
}