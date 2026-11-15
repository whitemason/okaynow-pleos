package com.whitemason.okaynow.app.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.whitemason.okaynow.app.MainActivity
import com.whitemason.okaynow.app.domain.flow.AccidentState
import com.whitemason.okaynow.app.presentation.accident.AccidentStore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 주행 중 백그라운드 사고 감시. 다른 앱(내비 등)이 화면에 있어도 [AccidentStore]의 감시가 이어지도록
 * 포그라운드 서비스로 프로세스를 유지한다.
 *
 * 포그라운드 서비스로 둔 근거(docs/SDK_NOTES.md 7절): Pleos 앱은 화면에서 사라져도 백그라운드에 남지만,
 * Android 14에서 백그라운드 앱의 일반 서비스는 정지되고 캐시된 프로세스는 동결될 수 있어 Vehicle SDK·CarPropertyManager
 * 콜백을 계속 받는다는 보장이 없다. Pleos 가이드가 요구하는 대로 상주 작업은 이 감시 하나로 제한한다.
 */
@AndroidEntryPoint
class DetectionService : Service() {

    @Inject lateinit var store: AccidentStore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
        ServiceCompat.startForeground(
            this, ONGOING_ID, ongoingNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        scope.launch {
            store.state.map { it.step.needsAttention() }.distinctUntilChanged().collect { attention ->
                if (attention) alert() else NotificationManagerCompat.from(this@DetectionService).cancel(ALERT_ID)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /** 사고 질문·긴급 단계에 들어가면 헤드업 알림을 띄우고 화면을 앞으로 가져오려 한다. */
    private fun alert() {
        // TODO: VoiceGuide(TTS)로 화면 없이도 질문을 읽어 준다(음성 우선 설계).
        val open = openAppIntent()
        if (canNotify()) {
            val notification = NotificationCompat.Builder(this, ALERT_CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("큰 충격이 감지됐어요")
                .setContentText("눌러서 사고 안내를 시작하세요.")
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(this).notify(ALERT_ID, notification)
        }
        // Android 10+에서는 백그라운드 액티비티 시작이 막힐 수 있다. 막히면 알림을 눌러 연다.
        try {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: RuntimeException) {
            Log.w(TAG, "could not bring app to front", e)
        }
    }

    private fun ongoingNotification(): Notification =
        NotificationCompat.Builder(this, ONGOING_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("사고 감지 중")
            .setContentText("충격이 감지되면 음성으로 안내를 시작합니다.")
            .setOngoing(true)
            .setContentIntent(openAppIntent())
            .build()

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun createChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(ONGOING_CHANNEL, "사고 감지", NotificationManager.IMPORTANCE_LOW),
        )
        manager.createNotificationChannel(
            NotificationChannel(ALERT_CHANNEL, "사고 경고", NotificationManager.IMPORTANCE_HIGH),
        )
    }

    private fun canNotify() =
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun AccidentState.needsAttention() = this is AccidentState.ConfirmAccident ||
        this is AccidentState.AskInjury || this == AccidentState.Emergency

    companion object {
        private const val TAG = "DetectionService"
        private const val ONGOING_CHANNEL = "detection"
        private const val ALERT_CHANNEL = "accident_alert"
        private const val ONGOING_ID = 1
        private const val ALERT_ID = 2

        /** 앱이 화면에 있을 때 호출한다(포그라운드 서비스는 백그라운드에서 시작할 수 없다). */
        fun start(context: Context) {
            context.startForegroundService(Intent(context, DetectionService::class.java))
        }
    }
}
