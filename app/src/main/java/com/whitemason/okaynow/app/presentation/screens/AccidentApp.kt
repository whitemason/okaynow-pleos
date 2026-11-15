package com.whitemason.okaynow.app.presentation.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whitemason.okaynow.app.domain.flow.AccidentState
import com.whitemason.okaynow.app.presentation.accident.AccidentIntent
import com.whitemason.okaynow.app.presentation.accident.AccidentUiEffect
import com.whitemason.okaynow.app.presentation.accident.AccidentUiState
import com.whitemason.okaynow.app.presentation.accident.AccidentViewModel
import com.whitemason.okaynow.app.presentation.components.LocalOnBack
import com.whitemason.okaynow.app.presentation.components.ParkRequired
import com.whitemason.okaynow.app.presentation.monitor.SignalMonitorScreen

/** ViewModel과 화면을 잇는다: 상태를 구독하고, 일회성 효과(다이얼)를 처리한다. */
@Composable
fun AccidentApp(viewModel: AccidentViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.uiEffects.collect { effect ->
            when (effect) {
                is AccidentUiEffect.OpenDialer -> context.openDialer(effect.number)
            }
        }
    }
    // C0은 화면이 보일 때만 "사고 났어요"를 듣는다(onStart~onStop).
    LifecycleStartEffect(viewModel) {
        viewModel.onIntent(AccidentIntent.AppVisibilityChanged(true))
        onStopOrDispose { viewModel.onIntent(AccidentIntent.AppVisibilityChanged(false)) }
    }
    AccidentScreen(state, viewModel::onIntent)
}

/**
 * 상태에 맞는 화면을 고른다(CLAUDE.md 규칙 5). 상태만 보고 그리며, 사용자 입력은 [onIntent] 하나로 보낸다.
 * 주행 중에는 C1~C3만 열고, C0은 수동 시작 버튼 하나만 보인다. C4~C9와 C0 설정은 P단에서만 연다.
 */
@Composable
fun AccidentScreen(state: AccidentUiState, onIntent: (AccidentIntent) -> Unit) {
    var showMonitor by rememberSaveable { mutableStateOf(false) }
    val emergency = { onIntent(AccidentIntent.RequestEmergency) }
    val next = { onIntent(AccidentIntent.Next) }

    if (showMonitor && state.step == AccidentState.Idle) {
        SignalMonitorScreen(onClose = { showMonitor = false })
        return
    }
    // 뒤로가기(툴바·하드웨어 키): C5~C9에서만. C1~C4는 안전 단계라 되돌리지 않는다.
    val back = if (state.canGoBack) ({ onIntent(AccidentIntent.Back) }) else null
    BackHandler(enabled = back != null) { back?.invoke() }

    CompositionLocalProvider(LocalOnBack provides back) {
        if (state.needsPark) {
            ParkRequired(nextStep = state.step.stepName(), onEmergency = emergency)
        } else when (state.step) {
            AccidentState.Idle -> HomeScreen(
                vehicle = state.vehicle,
                simulation = state.simulation,
                scenarios = state.scenarios,
                playing = state.playing,
                assessment = state.assessment,
                onManualStart = { onIntent(AccidentIntent.ManualStart) },
                onSimulationChange = { onIntent(AccidentIntent.SetSimulation(it)) },
                onPlay = { onIntent(AccidentIntent.PlayScenario(it)) },
                onOpenMonitor = { showMonitor = true },
                insurer = state.insurer,
                onSaveInsurer = { onIntent(AccidentIntent.SaveInsurer(it)) },
                savedReports = state.savedReports,
                onClearReports = { onIntent(AccidentIntent.ClearReports) },
            )
            is AccidentState.ConfirmAccident -> ConfirmAccidentScreen(
                secondsLeft = state.secondsLeft,
                voice = state.voice,
                onYes = { onIntent(AccidentIntent.AnswerYes) },
                onNo = { onIntent(AccidentIntent.AnswerNo) },
                onEmergency = emergency,
            )
            is AccidentState.AskInjury -> AskInjuryScreen(
                secondsLeft = state.secondsLeft,
                severe = (state.step as AccidentState.AskInjury).severe,
                voice = state.voice,
                onInjured = { onIntent(AccidentIntent.AnswerYes) },
                onNotInjured = { onIntent(AccidentIntent.AnswerNo) },
            )
            AccidentState.Emergency -> EmergencyScreen(
                location = state.location,
                onRepeatLocation = { onIntent(AccidentIntent.RepeatLocation) },
                onCall119 = { onIntent(AccidentIntent.Call119) },
                onDone = next,
            )
            AccidentState.SecureScene -> SecureSceneScreen(state.vehicle, next, { onIntent(AccidentIntent.Finish) }, emergency)
            AccidentState.VehicleCheck -> VehicleCheckScreen(state.vehicle, next, emergency)
            AccidentState.PhotoCapture -> PhotoCaptureScreen(
                zones = state.damageZones,
                onToggleZone = { onIntent(AccidentIntent.ToggleZone(it)) },
                onNext = next,
                onEmergency = emergency,
            )
            AccidentState.DamageMap -> DamageMapScreen(state.damageZones, { onIntent(AccidentIntent.ToggleZone(it)) }, next, emergency)
            AccidentState.ContactInsurer -> ContactInsurerScreen(
                report = state.report,
                insurer = state.insurer,
                onCallInsurer = { onIntent(AccidentIntent.CallInsurer) },
                onNext = next,
                onEmergency = emergency,
            )
            AccidentState.Done -> DoneScreen(state.report, state.summary, onFinish = { onIntent(AccidentIntent.Finish) })
        }
    }
}

/** 다이얼 화면만 연다(자동 발신 아님). 에뮬레이터에서 Pleos 전화 앱이 번호를 채워 여는 것 확인(2026-11-05). */
private fun Context.openDialer(number: String) {
    try {
        startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
    } catch (e: ActivityNotFoundException) {
        Log.w("AccidentApp", "no dialer", e)
    }
}

/** "차를 세우세요" 화면에서 알려 줄 다음 단계 이름. */
private fun AccidentState.stepName(): String = when (this) {
    AccidentState.SecureScene -> "2차 사고 막기"
    AccidentState.VehicleCheck -> "차량 점검"
    AccidentState.PhotoCapture -> "파손 부위 고르기"
    AccidentState.DamageMap -> "파손 부위 확인"
    AccidentState.ContactInsurer -> "보험사에 알리기"
    AccidentState.Done -> "처리 기록"
    else -> "다음 안내"
}
