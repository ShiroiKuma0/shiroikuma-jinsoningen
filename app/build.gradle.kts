import com.android.build.gradle.internal.tasks.factory.dependsOn
import java.io.File
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.compose)
}

// --- shiroikuma-jinsoningen fork: versioning + signing ---------------------------------------
// Upstream's own version literals (`latestVersionName` and `versionCode`, just below) stay
// untouched, so an upstream rebase brings the new base in by itself. Our fork tail derives
// from them:
//     versionName = "<upstream name>+<NNN>"        e.g. 0.7.4+001
//     versionCode = <upstream code> * 10000 + N    e.g. 740 * 10000 + 1 = 7400001
// N = BUILD_NUMBER in gradle.properties: bumped after every successful build by `buildFork`,
// reset to 1 on every upstream sync by the /upstream-new-version skill.
val forkBuildNumber = (project.findProperty("BUILD_NUMBER") as String?)?.trim()?.toIntOrNull() ?: 1
val forkPaddedBuildNumber = forkBuildNumber.toString().padStart(3, '0')

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) FileInputStream(keystorePropertiesFile).use { load(it) }
}

// Assigned inside defaultConfig below, consumed by `archivesName` / `buildFork` at the end.
var forkVersionName = ""
var forkVersionCode = 0

android {
    val latestVersionName = "0.7.5"
    namespace = "com.looker.droidify"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        // shiroikuma fork: our own applicationId, so we install side-by-side with upstream.
        // The `namespace` (com.looker.droidify) stays upstream's — renaming it would make every
        // rebase a mass-conflict, and nothing user-visible depends on it.
        applicationId = "shiroikuma.jinsoningen"
        minSdk = 23
        versionName = latestVersionName
        versionCode = 750

        // shiroikuma fork tail — see the block above the `android { }` scope.
        forkVersionCode = versionCode!! * 10000 + forkBuildNumber
        forkVersionName = "$versionName+$forkPaddedBuildNumber"
        versionCode = forkVersionCode
        versionName = forkVersionName

        testInstrumentationRunner = "com.looker.droidify.TestRunner"
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
            }
        }
    }

    androidResources.generateLocaleConfig = true

    buildTypes {
        release {
            // shiroikuma fork: sign from the gitignored keystore.properties.
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard.pro",
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = ".d"
        }
        all {
            buildConfigField(
                type = "String",
                name = "VERSION_NAME",
                // shiroikuma fork: report OUR version (e.g. 0.7.4+001), not upstream's "v0.7.4".
                value = "\"$forkVersionName\"",
            )
        }
    }

    packaging {
        resources {
            excludes += listOf(
                "/DebugProbesKt.bin",
                "/kotlin/**.kotlin_builtins",
                "/kotlin/**.kotlin_metadata",
                "/META-INF/**.kotlin_module",
                "/META-INF/**.pro",
                "/META-INF/**.version",
                "/META-INF/{AL2.0,LGPL2.1,LICENSE*}",
                "/META-INF/versions/9/previous-**.bin",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        viewBinding = true
        buildConfig = true
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
            all {
                it.useJUnitPlatform()
                val processor = Runtime.getRuntime().availableProcessors() / 2
                if (processor > 1) it.maxParallelForks = processor
            }
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xcontext-parameters")
        optIn.add("kotlin.RequiresOptIn")
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
        vendor.set(JvmVendorSpec.JETBRAINS)
    }
}

dependencies {
    implementation(libs.material)
    implementation(libs.core.ktx)
    implementation(libs.activity)
    implementation(libs.appcompat)
    implementation(libs.fragment.ktx)
    implementation(libs.lifecycle.viewModel)
    implementation(libs.recyclerview)
    implementation(libs.sqlite.ktx)

    implementation(libs.image.viewer)
    implementation(libs.bundles.coil)

    implementation(libs.datastore.core)
    implementation(libs.datastore.proto)

    implementation(libs.kotlin.stdlib)

    implementation(libs.bundles.coroutines)

    implementation(libs.libsu.core)
    implementation(libs.bundles.shizuku)

    implementation(libs.jackson.core)
    implementation(libs.serialization)

    implementation(libs.okhttp)
    implementation(libs.bundles.room)
    ksp(libs.room.compiler)

    implementation(libs.work.ktx)

    implementation(libs.hilt.core)
    implementation(libs.hilt.android)
    implementation(libs.hilt.ext.work)
    ksp(libs.hilt.compiler)
    ksp(libs.hilt.ext.compiler)

    // Compose dependencies
    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose)
    debugImplementation(libs.bundles.compose.debug)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.bundles.test.unit)
    testImplementation(libs.room.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.arch.core.testing)
    testImplementation(libs.test.core)
    testImplementation(libs.test.core.ktx)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.hilt.test)
    testRuntimeOnly(libs.junit.platform)
    testRuntimeOnly(libs.junit.vintage.engine)
    kspTest(libs.hilt.compiler)
    androidTestImplementation(libs.hilt.test)
    androidTestImplementation(libs.room.test)
    androidTestImplementation(libs.bundles.test.android)
    kspAndroidTest(libs.hilt.compiler)

//    debugImplementation(libs.leakcanary)
}

// using a task as a preBuild dependency instead of a function that takes some time insures that it runs
// in /res are (almost) all languages that have a translated string is saved. this is safer and saves some time
task("detectAndroidLocals") {
    val langsList: MutableSet<String> = HashSet()

    // in /res are (almost) all languages that have a translated string is saved. this is safer and saves some time
    fileTree("src/main/res").visit {
        if (this.file.path.endsWith("strings.xml") &&
            this.file.canonicalFile.readText().contains("<string")
        ) {
            var languageCode = this.file.parentFile.name.replace("values-", "")
            languageCode = if (languageCode == "values") "en" else languageCode
            langsList.add(languageCode)
        }
    }
    val langsListString = "{${langsList.sorted().joinToString(",") { "\"${it}\"" }}}"
    android.defaultConfig.buildConfigField("String[]", "DETECTED_LOCALES", langsListString)
}
tasks.preBuild.dependsOn("detectAndroidLocals")

// --- shiroikuma-jinsoningen fork: APK name + the one build task we use ------------------------
// Placed at the end of the script on purpose: forkVersionName / forkVersionCode are assigned
// while the `android { defaultConfig { } }` block above is evaluated.
base {
    archivesName = "shiroikuma-jinsoningen_$forkVersionName"
}

tasks.register("buildFork") {
    group = "build"
    description = "Build the signed release APK, copy it to ~/tmp, and bump BUILD_NUMBER."
    dependsOn("assembleRelease")

    // Configuration-cache-safe: capture every project-derived value HERE (configuration time).
    // The doLast lambda must not touch `layout` / `rootProject` / other project services.
    val apkName = "shiroikuma-jinsoningen_$forkVersionName.apk"
    val builtVersionCode = forkVersionCode
    val releaseApkDir = layout.buildDirectory.dir("outputs/apk/release")
    val userHome = providers.systemProperty("user.home")
    val propsFile = rootProject.file("gradle.properties")
    val currentBuildNumber = forkBuildNumber

    doLast {
        val outputDir = releaseApkDir.get().asFile
        val targetDir = File(userHome.get(), "tmp")
        targetDir.mkdirs()

        val apk = outputDir.listFiles { _, name -> name.endsWith(".apk") }?.firstOrNull()
            ?: throw GradleException("No APK found in $outputDir")
        val targetFile = File(targetDir, apkName)
        apk.copyTo(targetFile, overwrite = true)
        println("[1;36m>>> ${targetFile.absolutePath}[0m")
        println("[1;36m>>> versionCode $builtVersionCode[0m")

        // Auto-increment BUILD_NUMBER for the next build.
        val nextBuildNumber = currentBuildNumber + 1
        propsFile.writeText(
            propsFile.readText().replace(
                "BUILD_NUMBER=$currentBuildNumber",
                "BUILD_NUMBER=$nextBuildNumber",
            ),
        )
        println("[1;36m>>> BUILD_NUMBER bumped to $nextBuildNumber[0m")
    }
}
