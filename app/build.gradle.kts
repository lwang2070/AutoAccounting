import java.util.Calendar

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.ksp)
    alias(libs.plugins.materialThemeBuilder)
    alias(libs.plugins.autoresconfig)
}

autoResConfig {
    generateClass.set(true)
    generateRes.set(false)
    generatedClassFullName.set("net.ankio.auto.util.LangList")
    generatedArrayFirstItem.set("SYSTEM")
}

materialThemeBuilder {
    themes {
        for ((name, color) in listOf(
            "Default" to "6750A4",
            "Red" to "F44336",
            "Pink" to "E91E63",
            "Purple" to "9C27B0",
            "DeepPurple" to "673AB7",
            "Indigo" to "3F51B5",
            "Blue" to "2196F3",
            "LightBlue" to "03A9F4",
            "Cyan" to "00BCD4",
            "Teal" to "009688",
            "Green" to "4FAF50",
            "LightGreen" to "8BC3A4",
            "Lime" to "CDDC39",
            "Yellow" to "FFEB3B",
            "Amber" to "FFC107",
            "Orange" to "FF9800",
            "DeepOrange" to "FF5722",
            "Brown" to "795548",
            "BlueGrey" to "607D8F",
            "Sakura" to "FF9CA8"
        )) {
            create("Material$name") {
                lightThemeFormat = "ThemeOverlay.Light.%s"
                darkThemeFormat = "ThemeOverlay.Dark.%s"
                primaryColor = "#$color"
            }
        }
    }
    // Add Material Design 3 color tokens (such as palettePrimary100) in generated theme
    // rikka.material >= 2.0.0 provides such attributes
    generatePalette = true
}

// 发布时 CI 用 -PappVersionName=<tag 去掉 v> 传进来；本地编译用默认值。
val appVersionName = (findProperty("appVersionName") as String?) ?: "4.0.2"
val appVersionCode = calculateVersionCode(appVersionName)

// CI 通过环境变量注入发布签名。本地没有这些变量时回退到 debug 签名，
// 这样开发机不配密钥也能正常编。
val releaseKeystorePath: String? = System.getenv("KEYSTORE_FILE")

base {
    archivesName.set("app-$appVersionName($appVersionCode)")
}

android {
    namespace = "net.ankio.auto"
    compileSdk = 36


    defaultConfig {
        applicationId = "net.ankio.auto"
        minSdk = 29
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        flavorDimensions += "version"



        ndk {
            abiFilters.add("arm64-v8a")
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
        aidl = true
    }

    signingConfigs {
        if (releaseKeystorePath != null) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("debug") {
            ndk {
                abiFilters.clear()
                abiFilters.addAll(listOf("arm64-v8a", "x86_64"))
            }
        }
        getByName("release") {
            // 开启代码压缩以启用R8优化，但通过proguard规则禁用混淆
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 自动更新要求每次签名一致。CI 里没给密钥就直接失败，
            // 免得编出装不上的包还发出去；本地开发允许回退到 debug 签名。
            if (releaseKeystorePath == null) {
                if (System.getenv("CI") == "true") {
                    throw GradleException("CI 环境必须提供 KEYSTORE_FILE，拒绝用 debug 签名产出发布包")
                }
                logger.warn("未设置 KEYSTORE_FILE，release 包使用 debug 签名，无法覆盖已安装的正式版")
            }
            signingConfig = signingConfigs.getByName(if (releaseKeystorePath != null) "release" else "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }

    packaging {
        // 普通资源（非 .so）放这里
        resources {
            excludes += listOf(
                "META-INF/*"
            )
        }

        jniLibs {
            useLegacyPackaging = true
        }
    }






    androidResources {
        additionalParameters.addAll(
            listOf("--allow-reserved-package-id", "--package-id", "0x65")
        )
    }

    bundle {
        language {
            enableSplit = false
        }
    }

    lint {
        checkReleaseBuilds = true
        abortOnError = true
        warningsAsErrors = false
        fatal += setOf(
            "CheckResult",
            "InlinedApi",
            "NewApi",
            "Recycle",
            "MissingPermission",
            "WrongConstant",
            "LaunchActivityFromNotification",
        )
    }

}
fun calculateVersionCode(versionName: String): Int {
    // 位数分配：年年 月月 日日 次版本(3 位) 修订(3 位)。
    // 例：2026-11-01 发布 4.0.3 → 271100003。
    // 原来只有日期，同一天发两个版本会撞号，App 会认为没有新版本。
    // 日期在前保证「后发的版本号更大」，版本号在后保证同一天的先后顺序。
    // 上限 1006099999，低于安卓要求的 2100000000。
    val calendar = Calendar.getInstance()
    val year = calendar.get(Calendar.YEAR) % 100
    val month = calendar.get(Calendar.MONTH) + 1
    val day = calendar.get(Calendar.DAY_OF_MONTH)
    val parts = versionName.removePrefix("v").split(".")
    val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
    return (year * 10000000) + (month * 1000000) + (day * 100000) + (minor * 1000) + patch
}

configurations.configureEach {
    exclude("androidx.appcompat", "appcompat")
}

dependencies {


    implementation(libs.androidx.swiperefreshlayout)


    // 打包依赖
    implementation(libs.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.fragment.ktx)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.work.runtime.ktx)

    // Html转换
    implementation(libs.html.ktx)

    // gson
    implementation(libs.gson)

    // toast
    implementation(libs.toaster)
    implementation(libs.preferenceKtx)
    implementation(libs.androidx.lifecycle.service)

    // xp依赖
    compileOnly(libs.xposed)

    // flexbox
    implementation(libs.flexbox)

    // 圆角
    implementation(libs.round)


    // okhttp
    implementation(libs.okhttp)

    // Dex工具
    implementation(project(":dex"))

    // xml2json
    implementation(libs.xmltojson)

    implementation(project(":server"))
    implementation(project(":shell"))
    implementation(project(":ocr"))
    implementation(project(":tap"))

    // debug依赖
    debugImplementation(libs.leakcanary.android)



    implementation(libs.rikkaMaterial)
    implementation(libs.rikkaMaterialPreference)
    implementation(libs.about)

    implementation(libs.glide)

    implementation(libs.mmkv)
    implementation(libs.zip4j)
    implementation(kotlin("reflect"))


    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar", "*.aar"))))

    testImplementation(libs.junit)

}

