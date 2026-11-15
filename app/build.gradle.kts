plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    // 패키지명 확정(2026-11-09): 팀 Whitemason · 앱 OkayNow
    namespace = "com.whitemason.okaynow.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.whitemason.okaynow.app"
        minSdk = 28
        targetSdk = 34 // Pleos Connect Emulator = Android 14 (API 34)
        versionCode = 1
        versionName = "0.1.0"

        // 폰 촬영·기록 페이지(whitemason/okaynow-pages) 주소. 기본값은 배포 주소, 로컬 시험 때만 local.properties의 guide.baseUrl로 바꾼다.
        val guideBaseUrl = rootProject.file("local.properties").takeIf { it.exists() }
            ?.readLines()?.firstOrNull { it.startsWith("guide.baseUrl=") }?.substringAfter("=")?.trim()
            ?: "https://okaynow.whitemason.club/"
        buildConfigField("String", "GUIDE_BASE_URL", "\"$guideBaseUrl\"")
    }

    // AAOS CarPropertyManager(PERF_VEHICLE_SPEED). 실행 시 플랫폼이 제공하므로 APK에 포함되지 않는다.
    useLibrary("android.car")

    // release 서명: 키 파일과 비밀번호는 local.properties에만 둔다(커밋 금지, CLAUDE.md 규칙 7).
    // release.storeFile / release.storePassword / release.keyAlias / release.keyPassword
    val localProps = rootProject.file("local.properties").takeIf { it.exists() }
        ?.readLines().orEmpty().filter { "=" in it && !it.startsWith("#") }
        .associate { it.substringBefore("=").trim() to it.substringAfter("=").trim() }
    val releaseStore = localProps["release.storeFile"]?.let { file(it) }?.takeIf { it.exists() }
    signingConfigs {
        if (releaseStore != null) {
            create("release") {
                storeFile = releaseStore
                storePassword = localProps["release.storePassword"]
                keyAlias = localProps["release.keyAlias"]
                keyPassword = localProps["release.keyPassword"]
            }
        }
    }

    buildTypes {
        release {
            // 서명 키가 없으면 서명 안 된 APK가 나온다(설치 불가). 키 설정은 README "release APK" 참고.
            signingConfigs.findByName("release")?.let { signingConfig = it }
            // R8: 아이콘 라이브러리(material-icons-extended) 중 쓰지 않는 아이콘을 빼서 APK를 줄인다.
            // Pleos SDK는 proguard-rules.pro에서 통째로 지킨다(AIDL 바인딩).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true // 디버그 빌드 전용 화면 이동(스크린샷용)
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.pleos.vehicle)
    implementation(libs.pleos.tts)
    implementation(libs.pleos.stt)
    implementation(libs.pleos.fused.location)
    implementation(libs.pleos.navi.helper)
    implementation(libs.pleos.llm)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.zxing.core)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
