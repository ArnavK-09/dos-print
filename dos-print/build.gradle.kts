import java.util.Locale
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.detekt)
    alias(libs.plugins.spotless)
}

val appVersionName: String = providers.gradleProperty("VERSION_NAME").getOrElse("0.0.1")
val appVersionCode: Int = providers.gradleProperty("VERSION_CODE").getOrElse("1").toInt()

val signingProperties =
    Properties().apply {
        val file = rootProject.file("config/signing/signing.properties")
        if (file.exists()) {
            file.inputStream().use(::load)
        }
    }

android {
    namespace = "com.dosprint"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dosprint"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName

        vectorDrawables.useSupportLibrary = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (signingProperties.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(signingProperties.getProperty("storeFile"))
                storePassword = signingProperties.getProperty("storePassword")
                keyAlias = signingProperties.getProperty("keyAlias")
                keyPassword = signingProperties.getProperty("keyPassword")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
            isDebuggable = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                rootProject.file("config/proguard/consumer-rules.pro"),
            )
            if (signingProperties.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                signingConfig = signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = false
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            freeCompilerArgs.addAll(
                "-opt-in=kotlin.RequiresOptIn",
                "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
                "-Xjvm-default=all",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        localeFilters += listOf("en")
    }

    packaging {
        resources.excludes +=
            setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "META-INF/*.version",
                "kotlin/**",
                "**/*.kotlin_metadata",
                "DebugProbesKt.bin",
            )
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    lint {
        lintConfig = file("config/lint/lint-config.xml")
        warningsAsErrors = true
        abortOnError = true
        checkDependencies = true
        disable +=
            setOf(
                "GradleDependency",
                "AndroidGradlePluginVersion",
                "ObsoleteLintCustomCheck",
                "UnusedResources",
                "TypographyDashes",
                "UseKtx",
            )
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/kotlin")
        }
        getByName("test") {
            java.srcDirs("src/test/kotlin")
        }
        getByName("androidTest") {
            java.srcDirs("src/androidTest/kotlin")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.window)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.exifinterface)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3.window.size)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material3.adaptive.layout)
    implementation(libs.androidx.compose.material3.adaptive.navigation)
    implementation(libs.kyant.backdrop)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}

detekt {
    buildUponDefaultConfig = true
    parallel = true
    config.setFrom(files(rootProject.file("config/detekt/detekt.yml")))
    ignoreFailures = false
}

spotless {
    kotlin {
        target("src/**/*.kt")
        ktlint("1.5.0")
            .editorConfigOverride(
                mapOf(
                    "ktlint_standard_no-wildcard-imports" to "disabled",
                    "ktlint_standard_filename" to "disabled",
                    "ktlint_standard_max-line-length" to "120",
                    "ktlint_standard_function-naming" to "disabled",
                    "ktlint_standard_property-naming" to "disabled",
                    "ktlint_standard_class-signature" to "disabled",
                    "ktlint_standard_function-signature" to "disabled",
                    "ktlint_standard_multiline-expression-wrapping" to "disabled",
                    "ktlint_standard_string-template-indent" to "disabled",
                    "ktlint_standard_function-expression-body" to "disabled",
                    "ktlint_standard_blank-line-before-declaration" to "disabled",
                    "ktlint_standard_annotation" to "disabled",
                    "ktlint_standard_chain-method-continuation" to "disabled",
                    "ktlint_standard_trailing-comma-on-call-site" to "always",
                    "ktlint_standard_trailing-comma-on-declaration-site" to "always",
                ),
            )
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint("1.5.0")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

val releaseApkOutputPath = rootProject.projectDir.resolve("../build-outputs")
val releaseApkName: String = String.format(Locale.US, "dos-print-v%s.apk", appVersionName)

tasks.matching { it.name == "assembleRelease" }.configureEach {
    val apkDirPath =
        layout.buildDirectory
            .get()
            .asFile
            .resolve("outputs/apk/release")
    val destination = releaseApkOutputPath
    val targetName = releaseApkName
    doLast {
        val source =
            listOf(
                apkDirPath.resolve("dos-print-release.apk"),
                apkDirPath.resolve("dos-print-release-unsigned.apk"),
            ).firstOrNull { it.exists() }
        if (source != null) {
            destination.mkdirs()
            source.copyTo(destination.resolve(targetName), overwrite = true)
        } else {
            logger.warn("No release APK found in $apkDirPath")
        }
    }
}

tasks.named("clean") {
    delete(layout.buildDirectory)
}
