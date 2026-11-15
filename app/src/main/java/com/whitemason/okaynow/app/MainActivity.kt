package com.whitemason.okaynow.app

import android.Manifest
import android.car.Car
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import com.whitemason.okaynow.app.presentation.accident.AccidentStore
import com.whitemason.okaynow.app.presentation.screens.AccidentApp
import com.whitemason.okaynow.app.presentation.theme.AccidentTheme
import com.whitemason.okaynow.app.service.DetectionService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // 컨트롤러는 만들어지는 순간 신호 감시를 시작하므로, 권한 요청 전에 만들지 않도록 Lazy로 받는다.
    @Inject lateinit var store: dagger.Lazy<AccidentStore>

    // 속도 신호는 처음 구독할 때 권한을 확인하므로, 권한 요청이 끝난 뒤에 감시와 화면을 시작한다.
    private val permissionsResolved = mutableStateOf(false)

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onPermissionsResolved() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val missing = RUNTIME_PERMISSIONS.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) onPermissionsResolved() else requestPermissions.launch(missing.toTypedArray())
        setContent {
            AccidentTheme {
                if (permissionsResolved.value) AccidentApp()
            }
        }
    }

    private fun onPermissionsResolved() {
        DetectionService.start(this)
        permissionsResolved.value = true
    }

    private companion object {
        val RUNTIME_PERMISSIONS = listOf(
            Car.PERMISSION_SPEED,
            Manifest.permission.POST_NOTIFICATIONS,
            "pleos.car.permission.FUSED_LOCATION", // dangerous (FusedLocation 권한 선언 가이드)
        )
    }
}
