import com.android.build.gradle.internal.api.ApkVariantOutputImpl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.wire)
}

android {
    namespace = "com.helywin.leggedjoystick"
    compileSdk = 36

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
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    // 自定义 APK 文件名
    applicationVariants.all {
        this.outputs
            .map { it as ApkVariantOutputImpl }
            .forEach { output ->
                val variant = this.buildType.name
                var apkName = "RIDReceiver_" + this.versionName
                val dateFormat = SimpleDateFormat("yyyyMMddHHmm", Locale.getDefault())
                if (variant.isNotEmpty()) apkName += "_${variant}_"
                apkName += dateFormat.format(Date()) + ".apk"
                println("ApkName=$apkName ${this.buildType.name}")
                output.outputFileName = apkName

                // 将构建后的 APK 文件移动到 app 文件夹下
                // 在构建任务完成后执行文件复制操作
                tasks.named(
                    "assemble${
                        variant.replaceFirstChar {
                            if (it.isLowerCase()) it.uppercase()
                            else it.toString()
                        }
                    }"
                ).configure {
                    doLast {
                        val outputDir =
                            layout.buildDirectory.dir("outputs/apk/${variant}").get().asFile
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

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}