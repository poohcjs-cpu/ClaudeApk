import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// 서명 키: GitHub Actions 비밀값(환경변수) → keystore.properties → 없으면 저장소의 고정 디버그 키.
// 같은 키로 서명해야 새 버전을 기존 앱 위에 덮어 설치할 수 있다.
val keyProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(env: String, prop: String): String? = System.getenv(env)?.takeIf { it.isNotBlank() } ?: keyProps.getProperty(prop)

val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "io.github.poohcjs.fitkey"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.poohcjs.fitkey"
        minSdk = 26
        targetSdk = 36
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
    }

    signingConfigs {
        create("release") {
            val storePath = signingValue("SIGNING_STORE_FILE", "storeFile")
            if (storePath != null) {
                storeFile = file(storePath)
                storePassword = signingValue("SIGNING_STORE_PASSWORD", "storePassword")
                keyAlias = signingValue("SIGNING_KEY_ALIAS", "keyAlias")
                keyPassword = signingValue("SIGNING_KEY_PASSWORD", "keyPassword")
            } else {
                storeFile = rootProject.file("signing/shared-debug.keystore")
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
        getByName("debug") {
            storeFile = rootProject.file("signing/shared-debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            // 실제 기기에서 시험할 수 없는 환경이라, 테스트한 코드와 설치되는 코드를 똑같이 두려고 압축(R8)을 끈다
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // 사전 파일은 이미 gzip으로 줄여 두었다
    androidResources { noCompress += "gz" }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { test ->
                if (!project.hasProperty("screenshots")) test.exclude("**/ScreenshotTest*")
                test.testLogging { showStandardStreams = project.hasProperty("verbose") }
                test.systemProperty("screenshots.dir", layout.buildDirectory.dir("screenshots").get().asFile.absolutePath)
            }
        }
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
