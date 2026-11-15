package com.whitemason.okaynow.app.presentation.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Shield
import androidx.compose.runtime.Composable
import com.whitemason.okaynow.app.presentation.components.BigButton
import com.whitemason.okaynow.app.presentation.components.StepScaffold
import com.whitemason.okaynow.app.presentation.components.Tile
import com.whitemason.okaynow.app.presentation.components.TileGrid
import com.whitemason.okaynow.app.presentation.components.LocalPortrait

/** 전체 안내(웹). 문구를 바꾸면 whitemason/okaynow-pages의 privacy.html도 맞춘다. */
const val PRIVACY_URL = "okaynow.whitemason.club/privacy.html"

/** C9 QR 아래에 붙이는 한 줄. 기록이 차 밖(폰)으로 나가는 유일한 순간이다. */
const val QR_PRIVACY_NOTE = "기록은 QR 주소 안에만 담겨요. 서버로 보내지 않아요"

/**
 * C0 개인정보 안내(P단에서만). 닫기는 툴바 뒤로가기(LocalOnBack). 앱이 무엇을 어디에 두는지와, 차 안에 보관한 사고 기록 지우기.
 * 사실 근거: 기록은 앱 내부 files/reports(FileReportArchive), 보험사는 DataStore, 음성·요약은 Gleo ON_DEVICE,
 * 사진은 폰에만(PhotoCaptureScreen), QR은 주소의 # 뒤(ReportLink). 서버 전송이 생기면(파손 분석) 이 문구를 고친다.
 */
@Composable
fun PrivacyScreen(savedReports: Int, onClearReports: () -> Unit) {
    StepScaffold(
        title = "개인정보 안내",
        subtitle = "서버로 보내지 않아요 · 자세히: $PRIVACY_URL",
        wide = true,
        actions = {
            BigButton(
                "기록 지우기",
                onClearReports,
                primary = false,
                enabled = savedReports > 0,
                caption = if (savedReports > 0) "저장된 기록 ${savedReports}건" else "저장된 기록 없음",
                icon = Icons.Filled.Delete,
            )
        },
    ) {
        TileGrid(PRIVACY_TILES, columns = if (LocalPortrait.current) 2 else 3)
    }
}

private val PRIVACY_TILES = listOf(
    Tile("위치", "사고 때만 확인해요", caption = "119 안내·요약에만 써요", icon = Icons.Filled.LocationOn, compact = true),
    Tile("음성", "차 안에서 처리해요", caption = "녹음은 저장하지 않아요", icon = Icons.Filled.Mic, compact = true),
    Tile("사고 기록", "이 차에만 저장해요", caption = "시각·위치·부상·파손 등", icon = Icons.Filled.Description, compact = true),
    Tile("보험사", "이 차에만 저장해요", caption = "전화는 직접 걸어요", icon = Icons.Filled.Shield, compact = true),
    Tile("QR", "폰으로 바로 넘겨요", caption = "폰 방문 기록에 남아요", icon = Icons.Filled.QrCode2, compact = true),
    Tile("사진", "폰에만 저장해요", caption = "앱으로 받지 않아요", icon = Icons.Filled.PhotoCamera, compact = true),
)
