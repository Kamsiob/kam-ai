import java.util.Properties

// AGP 9 compiles Kotlin itself. The standalone org.jetbrains.kotlin.android
// plugin is not applied here, and applying it is now an error. See DECISIONS.md.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// The upload keystore lives outside the repository, in the secrets directory, so
// no signing material is ever committed. A machine without it still builds debug
// and an unsigned release; only signing a release for distribution needs it.
val keystorePropsFile = file("${System.getProperty("user.home")}/.kamsiob-secrets/keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

// Migrations and the Android-backed tests are verified on an emulator, never
// against the phone, which holds the owner's real data and carries exactly one
// installation of this app. An emulator is x86_64 while the app ships arm64
// only, so an emulator build swaps the ABI and leaves the native inference
// stack out entirely: every native library is loaded lazily at first use, and a
// migration test never downloads a model, so nothing calls into one.
//   ./gradlew assembleDebug -Pkamai.emulator=true
// This must never be used for a build that goes on the phone.
//
// The emulator does not currently run on this build machine: its qemu process
// segfaults at startup regardless of GPU mode, acceleration, or ASLR. See
// DECISIONS.md, "Where migrations get tested". Until that is resolved the
// runnable proof is MigrationSqlTest, a pure JVM test over real SQLite.
val emulatorBuild = providers.gradleProperty("kamai.emulator").orNull == "true"

android {
    namespace = "com.kamsiob.kamai"
    // Compiled against 37 because current AndroidX libraries require it.
    // targetSdk stays at 36, which is what Play requires. Compiling against a
    // newer platform and targeting an older one is the supported combination.
    compileSdk = 37

    ndkVersion = providers.gradleProperty("kamai.ndkVersion").get()

    defaultConfig {
        applicationId = "com.kamsiob.kamai"
        minSdk = 31
        targetSdk = 36
        // 1.0.0, and version code 1. First public release, and the store has
        // never seen a bundle for this package, so nothing has to be climbed past.
        // Semantic versioning makes this 1.0.0 rather than 0.x: the app is
        // complete for the scope it claims, and 0.x would tell people it is not.
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        // arm64 only. Every phone with enough memory to hold one of these models
        // is arm64, and shipping the other ABIs would only pad the download.
        // The one exception is an emulator build, which is x86_64 and carries no
        // native code at all.
        ndk {
            abiFilters += if (emulatorBuild) "x86_64" else "arm64-v8a"
        }

        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DCMAKE_BUILD_TYPE=Release",
                    "-DGGML_OPENMP=OFF",
                    "-DGGML_LLAMAFILE=OFF",
                )
                cppFlags += "-O3"
            }
        }
    }

    if (!emulatorBuild) {
        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
                version = providers.gradleProperty("kamai.cmakeVersion").get()
            }
        }
    }

    signingConfigs {
        create("release") {
            if (keystoreProps.isNotEmpty()) {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Always the same application id, so a debug build upgrades the one
            // installed copy in place. There is exactly one copy of this app on
            // the phone, ever. See DECISIONS.md, "One copy only".
            applicationIdSuffix = ""
            isMinifyEnabled = false
        }
        // The release build's minification, shrinking and keep rules, signed with
        // the debug key so it installs over an existing debug build in place.
        //
        // This exists because the thing that breaks at release is minification,
        // not signing, and the only way to test minification used to be to
        // uninstall the app first: the release signature differs, so Android
        // refuses the upgrade. Uninstalling destroys the Keystore entry that
        // wraps the database key, which makes every existing conversation
        // permanently unreadable, and costs a multi-gigabyte model re-download
        // on top. Paying that to test a ProGuard rule is a bad trade.
        //
        // Identical to release in every way that can break at runtime. It is not
        // a shippable artifact and is never uploaded: the signature is wrong for
        // that on purpose.
        create("releaseCheck") {
            initWith(getByName("release"))
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            // initWith does not copy proguardFiles. Without this the build runs
            // R8 with none of our keep rules, which is worse than not testing
            // minification at all: it would strip the JNI bridges and then fail
            // in a way that looks like a native bug rather than a missing rule.
            // Caught because R8 stopped on a missing class our rules already
            // suppress, and there was no configuration.txt to explain it.
            setProguardFiles(
                listOf(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro",
                ),
            )
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Signed only when the upload keystore is present.
            if (keystoreProps.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = false
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

// Robolectric 4.16.1 cannot instrument against a JDK 26 class file, and this machine's
// default JDK is 26, so six test classes failed identically at ClassReader.java:200 for as
// long as anyone had been working here. Thirty-nine failures is a lot of noise to read
// past, and real failures have hidden inside it before.
//
// Only the unit test task is moved to 21. Compilation, KSP, AGP and the native build all
// still run on 26, so nothing about what ships changes; this picks the JVM the tests
// execute on and nothing else. The path Gradle finds 21 through is in gradle.properties.
tasks.withType<Test>().configureEach {
    javaLauncher.set(
        javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(21))
        },
    )
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.pdfbox.android)
    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.sqlite)
    implementation(libs.androidx.sqlite.ktx)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.test.core)
    // A real SQLite engine on the JVM, so migration SQL can be verified on a
    // build machine with no device and no working emulator. See DECISIONS.md.
    testImplementation(libs.sqlite.jdbc)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.uiautomator)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
