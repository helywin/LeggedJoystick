import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.wire)
}

android {
    namespace = "com.helywin.leggedjoystick"
    compileSdk = 37

    // 版本管理
    val versionMajor = 1
    val versionMinor = 0
    val versionPatch = 2

    signingConfigs {
        getByName("debug") {
            storeFile = file("..\\key\\helywin.jks")
            storePassword = "jiangwq."
            keyAlias = "helywin"
            keyPassword = "jiangwq."
        }
    }
    defaultConfig {
        applicationId = "com.helywin.leggedjoystick"
        minSdk = 26
        targetSdk = 36
        versionCode = versionMajor * 10000 + versionMinor * 100 + versionPatch
        versionName = "${versionMajor}.${versionMinor}.${versionPatch}"
        signingConfig = signingConfigs.getByName("debug")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

androidComponents {
    // 自定义 APK 文件名，并在 assemble 后复制到 app/output。
    val apkBaseName = "LeggedJoystick"
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val buildTypeName = variant.name
            val versionName = output.versionName.orNull ?: "0.0.0"
            val dateFormat = SimpleDateFormat("yyyyMMddHHmm", Locale.getDefault())
            val apkName = "${apkBaseName}_${versionName}_${buildTypeName}_${dateFormat.format(Date())}.apk"
            output.outputFileName.set(apkName)

            val assembleTaskName = "assemble${
                buildTypeName.replaceFirstChar {
                    if (it.isLowerCase()) it.uppercase()
                    else it.toString()
                }
            }"
            tasks.matching { it.name == assembleTaskName }.configureEach {
                doLast {
                    val outputDir = layout.buildDirectory.dir("outputs/apk/${buildTypeName}").get().asFile
                    val destinationDir = file("${project.projectDir}/output")
                    copy {
                        from(outputDir)
                        into(destinationDir)
                        include(apkName)
                    }
                }
            }
        }
    }
}

wire {
    kotlin {
        android = true
        javaInterop = true
    }
    sourcePath {
        srcDir("../proto")
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.jeromq)
    implementation(libs.timber)
    implementation(libs.gson)
    implementation(libs.wire.runtime)
    implementation(libs.coil.compose)
    implementation(libs.ijkplayer.java)
    implementation(libs.ijkplayer.arm64)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
