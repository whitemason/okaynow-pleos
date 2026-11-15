plugins {
    alias(libs.plugins.android.application) apply false
    // AGP 9 built-in Kotlin이 쓸 Kotlin 버전을 고정하기 위해 classpath에만 올린다(적용하지 않음).
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
