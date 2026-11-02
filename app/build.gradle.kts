import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
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
    namespace = "io.github.poohcjs.hotdealradar"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.poohcjs.hotdealradar"
        minSdk = 26
        targetSdk = 36
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
        // 이 저장소의 수집기(collector-service)가 30분마다 올리는 결과. 받지 못하면 예전 웹앱 주소로 다시 시도한다.
        buildConfigField("String", "DEFAULT_DATA_URL", "\"https://raw.githubusercontent.com/poohcjs-cpu/ClaudeApk/data/deals.json\"")
        buildConfigField("String", "FALLBACK_DATA_URL", "\"https://poohcjs-cpu.github.io/hotdeal-radar/data/deals.json\"")
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
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { test ->
                // 화면 그림 테스트(ScreenshotTest)는 -Pscreenshots 를 줄 때만 돈다
                if (!project.hasProperty("screenshots")) test.exclude("**/ScreenshotTest*")
                // 실제 다나와에 물어보는 확인은 -Plive 를 줄 때만
                if (!project.hasProperty("live")) test.exclude("**/LiveNetworkTest*")
                test.testLogging { showStandardStreams = project.hasProperty("live") }
                test.systemProperty("screenshots.dir", layout.buildDirectory.dir("screenshots").get().asFile.absolutePath)
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
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
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.work.runtime)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.jsoup)
    implementation(libs.coil.compose)
    implementation(libs.coil.network)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
