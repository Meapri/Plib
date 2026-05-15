plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.chanwoo.androlinux"
    compileSdk = 35

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    defaultConfig {
        applicationId = "dev.chanwoo.androlinux"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        ndkVersion = "27.2.12479018"

        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++20", "-Wall", "-Wextra", "-Werror")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    sourceSets.getByName("main") {
        jniLibs.srcDir(layout.buildDirectory.dir("generated/native-test-command/jniLibs"))
    }
}

tasks.register("packageNativeTestCommand") {
    val generatedDir = layout.buildDirectory.dir("generated/native-test-command/jniLibs")
    outputs.dir(generatedDir)
    doLast {
        val abis = listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        abis.forEach { abi ->
            val built = fileTree(layout.buildDirectory.dir("intermediates/cxx/Debug")) {
                include("**/obj/$abi/alr-test-command")
            }.files.singleOrNull()
                ?: throw GradleException("missing alr-test-command for $abi; run buildCMakeDebug[$abi] first")
            val destDir = generatedDir.get().dir(abi).asFile
            destDir.mkdirs()
            built.copyTo(destDir.resolve("libalr_test_command.so"), overwrite = true)
        }
    }
}

tasks.matching { it.name == "mergeDebugJniLibFolders" }.configureEach {
    dependsOn("packageNativeTestCommand")
}

tasks.matching { it.name.startsWith("buildCMakeDebug") }.configureEach {
    finalizedBy("packageNativeTestCommand")
}

dependencies {
    implementation("org.apache.commons:commons-compress:1.26.2")
}
