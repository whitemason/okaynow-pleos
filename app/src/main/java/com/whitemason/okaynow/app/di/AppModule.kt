package com.whitemason.okaynow.app.di

import android.os.SystemClock
import android.util.Log
import com.whitemason.okaynow.app.data.location.LocationRepository
import com.whitemason.okaynow.app.data.report.DataStoreInsurerRepository
import com.whitemason.okaynow.app.data.report.FileReportArchive
import com.whitemason.okaynow.app.data.report.PleosSummaryWriter
import com.whitemason.okaynow.app.data.vehicle.SignalSourceSelector
import com.whitemason.okaynow.app.data.voice.PleosVoiceGuide
import com.whitemason.okaynow.app.domain.common.Clock
import com.whitemason.okaynow.app.domain.common.FlowLogger
import com.whitemason.okaynow.app.domain.location.LocationSource
import com.whitemason.okaynow.app.domain.report.InsurerRepository
import com.whitemason.okaynow.app.domain.report.ReportArchive
import com.whitemason.okaynow.app.domain.report.SummaryWriter
import com.whitemason.okaynow.app.domain.vehicle.SignalSourceRepository
import com.whitemason.okaynow.app.domain.voice.VoiceGuide
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** domain 인터페이스 ← data 구현 연결. presentation은 domain 인터페이스만 안다. */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
    @Binds
    abstract fun voiceGuide(impl: PleosVoiceGuide): VoiceGuide

    @Binds
    abstract fun signalSources(impl: SignalSourceSelector): SignalSourceRepository

    @Binds
    abstract fun location(impl: LocationRepository): LocationSource

    @Binds
    abstract fun insurers(impl: DataStoreInsurerRepository): InsurerRepository

    @Binds
    abstract fun summaryWriter(impl: PleosSummaryWriter): SummaryWriter

    @Binds
    abstract fun reportArchive(impl: FileReportArchive): ReportArchive

    companion object {
        @Provides
        fun clock(): Clock = Clock { SystemClock.elapsedRealtime() }

        @Provides
        fun logger(): FlowLogger = FlowLogger { Log.i("AccidentFlow", it) }
    }
}
