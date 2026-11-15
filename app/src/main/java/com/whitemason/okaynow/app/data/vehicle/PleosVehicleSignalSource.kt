package com.whitemason.okaynow.app.data.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.api.model.telltale.Telltales
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.telltale.TelltaleState
import ai.pleos.playground.vehicle.constant.telltale.TelltaleType
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.LightStateListener
import ai.pleos.playground.vehicle.listener.PowerDownWarningIndicatorListener
import ai.pleos.playground.vehicle.listener.SeatAirbagDeployedListener
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.whitemason.okaynow.app.domain.vehicle.ExteriorLight
import com.whitemason.okaynow.app.domain.vehicle.Gear
import com.whitemason.okaynow.app.domain.vehicle.SafetyWarning
import com.whitemason.okaynow.app.domain.vehicle.SignalType
import com.whitemason.okaynow.app.domain.vehicle.SpeedSample
import com.whitemason.okaynow.app.domain.vehicle.TireStatus
import com.whitemason.okaynow.app.domain.vehicle.VehicleSignalSource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Pleos Vehicle SDK v2.3.2 + AAOS CarPropertyManager 기반 구현.
 * API는 docs/SDK_NOTES.md와 Vehicle-2.3.2.aar의 시그니처로 확인한 것만 쓴다.
 *
 * 각 신호는 처음 구독될 때 capability를 확인하고, 미지원이면 값을 내보내지 않는다.
 */
@Singleton
class PleosVehicleSignalSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : VehicleSignalSource {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val vehicle = Vehicle(context).apply { initialize() }
    private val brake = vehicle.getBrake()
    private val door = vehicle.getDoor()
    private val gearApi = vehicle.getGear()
    private val safety = vehicle.getSafety()
    private val light = vehicle.getLight()

    private val supported = MutableStateFlow<Set<SignalType>>(emptySet())
    override val supportedSignals: StateFlow<Set<SignalType>> = supported.asStateFlow()

    override val speed: Flow<SpeedSample> = signal(SignalType.SPEED, ::speedFlowOrNull)

    override val brakePedal: Flow<Boolean> = signal(SignalType.BRAKE_PEDAL) {
        val readable = checkCapability("brakePedal") { ok, fail ->
            brake.checkBrakePedalStatusCapabilities({ config -> ok(config.isReadable()) }, fail)
        }
        if (!readable) return@signal null
        callbackFlow {
            val listener = object : BrakePedalStatusListener {
                override fun onBrakePedalStatusUpdated(pressed: Boolean?) {
                    pressed?.let { trySend(it) }
                }
                override fun onFailed(e: Exception) = logFailure("brakePedal listener", e)
            }
            brake.getCurrentBrakePedalStatus({ pressed -> pressed?.let { trySend(it) } }, { logFailure("brakePedal get", it) })
            brake.registerBrakePedalStatus(listener)
            awaitClose { brake.unregisterBrakePedalStatus(listener) }
        }
    }

    // TODO(verify-sdk): Vehicle SDK에 비상등 API가 없다(TurnSignalValue = OFF/LEFT/RIGHT).
    //  AAOS HAZARD_LIGHTS_STATE는 권한 이름·승인 여부 미확인이라 연결하지 않는다.
    override val hazardLight: Flow<Boolean> = emptyFlow()

    // Gear API는 공식 문서에 없고 AAR에만 있다(대장 승인 2026-11-03). capability API가 없으므로
    // 첫 조회가 성공해야 지원으로 본다.
    override val gear: Flow<Gear> = signal(SignalType.GEAR) {
        val initial = withRetry<VehicleGear?>("gear") { ok, fail -> gearApi.getCurrentGearSelectionState(ok, fail) }
            .getOrElse { return@signal null }
        callbackFlow {
            initial.toGear()?.let { trySend(it) }
            val listener = object : GearSelectionListener {
                override fun onSelected(gear: VehicleGear?) {
                    gear.toGear()?.let { trySend(it) }
                }
                override fun onFailed(e: Exception) = logFailure("gear listener", e)
            }
            gearApi.registerGearSelection(listener)
            awaitClose { gearApi.unregisterGearSelection(listener) }
        }
    }

    override val doorOpen: Flow<Boolean> = signal(SignalType.DOOR_OPEN) {
        val bounds = withRetry("doorPosition") { ok, fail ->
            door.checkDoorPositionCapabilities(
                { config: CapabilityConfig, areaMap -> ok(if (config.isReadable()) areaMap else emptyMap()) },
                fail,
            )
        }.getOrNull()
            // 승객 도어만 본다(후드·트렁크 제외).
            ?.filterKeys { area -> area.values.all { it !in NON_CABIN_DOORS } }
            ?.mapValues { (_, bound) -> bound.minValue ?: 0 }
        if (bounds.isNullOrEmpty()) return@signal null
        callbackFlow {
            val positions = mutableMapOf<DoorArea, Int>()
            fun publish() = trySend(positions.any { (area, pos) -> pos > bounds.getValue(area) })
            val listeners = bounds.keys.associateWith { area ->
                object : DoorPositionListener {
                    override fun onPositionUpdated(position: Int?) {
                        if (position == null) return
                        synchronized(positions) { positions[area] = position; publish() }
                    }
                    override fun onFailed(e: Exception) = logFailure("door listener $area", e)
                }
            }
            listeners.forEach { (area, listener) -> door.registerDoorPosition(area, listener) }
            awaitClose { listeners.forEach { (area, listener) -> door.unregisterDoorPosition(area, listener) } }
        }.distinctUntilChanged()
    }

    override val safetyWarnings: Flow<Set<SafetyWarning>> = signal(SignalType.SAFETY_WARNINGS) {
        val parts = listOfNotNull(airbagDeployedFlowOrNull(), powerDownFlowOrNull(), telltaleWarningsFlowOrNull())
        if (parts.isEmpty()) return@signal null
        combine(parts.map { part -> part.onStart { emit(emptySet()) } }) { sets -> sets.flatMap { it }.toSet() }
            .distinctUntilChanged()
    }

    override val tireStatus: Flow<TireStatus> = signal(SignalType.TIRE_STATUS) {
        // Tire.getTirePressure는 PRIVATE_CAR_SAFETY라 쓰지 않는다. TPMS 경고등으로 판단.
        if (!telltaleProbe.await()) return@signal null
        telltales.map { telltales ->
            if (telltales.isActive(TelltaleType.TPMS)) TireStatus.LOW_PRESSURE_WARNING else TireStatus.NORMAL
        }.distinctUntilChanged()
    }

    override val exteriorLights: Flow<Set<ExteriorLight>> = signal(SignalType.EXTERIOR_LIGHTS) {
        val parts = lightApis().mapNotNull { api ->
            val readable = checkCapability("light ${api.type}") { ok, fail ->
                api.check({ config, _ -> ok(config.isReadable()) }, fail)
            }
            if (!readable) return@mapNotNull null
            callbackFlow {
                fun publish(state: LightState?) {
                    state?.let { trySend(if (it.isOn()) setOf(api.type) else emptySet()) }
                }
                val listener = object : LightStateListener {
                    override fun onLightStateUpdated(state: LightState?) = publish(state)
                    override fun onFailed(e: Exception) = logFailure("light ${api.type} listener", e)
                }
                api.get(::publish) { logFailure("light ${api.type} get", it) }
                api.register(listener)
                awaitClose { api.unregister(listener) }
            }.onStart { emit(emptySet()) }
        }
        if (parts.isEmpty()) return@signal null
        combine(parts) { sets -> sets.flatMap { it }.toSet() }.distinctUntilChanged()
    }

    private class LightApi(
        val type: ExteriorLight,
        val check: (onComplete: (CapabilityConfig, List<LightState>) -> Unit, onFailed: (Exception) -> Unit) -> Unit,
        val get: (onComplete: (LightState?) -> Unit, onFailed: (Exception) -> Unit) -> Unit,
        val register: (LightStateListener) -> Unit,
        val unregister: (LightStateListener) -> Unit,
    )

    private fun lightApis() = listOf(
        LightApi(
            ExteriorLight.LOW_BEAM,
            { ok, fail -> light.checkLowBeamLightStateCapabilities(ok, fail) },
            { ok, fail -> light.getCurrentLowBeamLightState(ok, fail) },
            light::registerLowBeamLightState, light::unregisterLowBeamLightState,
        ),
        LightApi(
            ExteriorLight.HIGH_BEAM,
            { ok, fail -> light.checkHighBeamLightStateCapabilities(ok, fail) },
            { ok, fail -> light.getCurrentHighBeamLightState(ok, fail) },
            light::registerHighBeamLightState, light::unregisterHighBeamLightState,
        ),
        LightApi(
            ExteriorLight.TAIL_LAMP,
            { ok, fail -> light.checkTailLampLightStateCapabilities(ok, fail) },
            { ok, fail -> light.getCurrentTailLampLightState(ok, fail) },
            light::registerTailLampLightState, light::unregisterTailLampLightState,
        ),
        LightApi(
            ExteriorLight.FRONT_FOG,
            { ok, fail -> light.checkFrontFogLightStateCapabilities(ok, fail) },
            { ok, fail -> light.getCurrentFrontFogLightState(ok, fail) },
            light::registerFrontFogLightState, light::unregisterFrontFogLightState,
        ),
        LightApi(
            ExteriorLight.REAR_FOG,
            { ok, fail -> light.checkRearFogLightStateCapabilities(ok, fail) },
            { ok, fail -> light.getCurrentRearFogLightState(ok, fail) },
            light::registerRearFogLightState, light::unregisterRearFogLightState,
        ),
    )

    // registerTelltales는 PRIVATE_CAR_SAFETY라서 CAR_SAFETY인 getTelltalesInfo를 주기적으로 조회한다.
    private val telltaleProbe = scope.async(start = CoroutineStart.LAZY) {
        withRetry<Telltales>("telltales") { ok, fail -> safety.getTelltalesInfo(ok, fail) }.isSuccess
    }

    private val telltales: Flow<Telltales> = flow {
        while (true) {
            call<Telltales> { ok, fail -> safety.getTelltalesInfo(ok, fail) }
                .onSuccess { emit(it) }
                .onFailure { logFailure("telltales poll", it) }
            delay(TELLTALE_POLL_MS)
        }
    }.shareIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), replay = 1)

    /** 앱 종료 시 호출. 콜백이 끝난 뒤 SDK를 해제한다. */
    fun close() {
        scope.cancel()
        vehicle.release()
    }

    private suspend fun airbagDeployedFlowOrNull(): Flow<Set<SafetyWarning>>? {
        val seats = withRetry("airbagDeployed") { ok, fail ->
            safety.checkAirbagDeployedCapabilities(
                { config: CapabilityConfig, seats -> ok(if (config.isReadable()) seats else emptyList()) },
                fail,
            )
        }.getOrNull()
        if (seats.isNullOrEmpty()) return null
        return callbackFlow {
            val deployed = mutableMapOf<SeatArea, Boolean>()
            val listeners = seats.associateWith { seat ->
                object : SeatAirbagDeployedListener {
                    override fun onSeatAirbagDeployUpdated(isDeployed: Boolean?) {
                        if (isDeployed == null) return
                        synchronized(deployed) {
                            deployed[seat] = isDeployed
                            trySend(if (deployed.containsValue(true)) setOf(SafetyWarning.AIRBAG_DEPLOYED) else emptySet())
                        }
                    }
                    override fun onFailed(e: Exception) = logFailure("airbag listener $seat", e)
                }
            }
            listeners.forEach { (seat, listener) -> safety.registerSeatAirbagDeployed(seat, listener) }
            awaitClose { listeners.forEach { (seat, listener) -> safety.unregisterSeatAirbagDeployed(seat, listener) } }
        }
    }

    private suspend fun powerDownFlowOrNull(): Flow<Set<SafetyWarning>>? {
        val readable = checkCapability("powerDown") { ok, fail ->
            safety.checkPowerDownWarningIndicatorCapabilities({ config -> ok(config.isReadable()) }, fail)
        }
        if (!readable) return null
        return callbackFlow {
            fun publish(on: Boolean?) {
                on?.let { trySend(if (it) setOf(SafetyWarning.POWER_DOWN) else emptySet()) }
            }
            val listener = object : PowerDownWarningIndicatorListener {
                override fun onPowerDownWarningIndicatorUpdated(on: Boolean?) = publish(on)
                override fun onFailed(e: Exception) = logFailure("powerDown listener", e)
            }
            safety.hasPowerDownWarningIndicator(::publish) { logFailure("powerDown get", it) }
            safety.registerPowerDownWarningIndicator(listener)
            awaitClose { safety.unregisterPowerDownWarningIndicator(listener) }
        }
    }

    private suspend fun telltaleWarningsFlowOrNull(): Flow<Set<SafetyWarning>>? {
        if (!telltaleProbe.await()) return null
        // TODO(verify-sdk): Telltales 목록에 꺼진 경고등도 포함되는지 문서에 없음. 상태(OFF/ON/BLINK)로만 판단한다.
        return telltales.map { telltales ->
            TELLTALE_WARNINGS.filterKeys { telltales.isActive(it) }.values.toSet()
        }
    }

    // TODO(verify-sdk): PERF_VEHICLE_SPEED 갱신 주기와 Pleos 심사 승인 여부 미확인(SDK_NOTES 5절).
    private fun speedFlowOrNull(): Flow<SpeedSample>? {
        if (context.checkSelfPermission(Car.PERMISSION_SPEED) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "speed: ${Car.PERMISSION_SPEED} not granted")
            return null
        }
        val car = runCatching { Car.createCar(context) }.getOrNull() ?: return null
        val properties = car.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
        if (properties?.getCarPropertyConfig(VehiclePropertyIds.PERF_VEHICLE_SPEED) == null) {
            Log.w(TAG, "speed: PERF_VEHICLE_SPEED not supported")
            car.disconnect()
            return null
        }
        return callbackFlow {
            val callback = object : CarPropertyManager.CarPropertyEventCallback {
                override fun onChangeEvent(value: CarPropertyValue<*>) {
                    val mps = value.value as? Float ?: return
                    trySend(SpeedSample(mps, value.timestamp / NANOS_PER_MILLI))
                }
                override fun onErrorEvent(propertyId: Int, areaId: Int) {
                    Log.w(TAG, "speed: error event property=$propertyId area=$areaId")
                }
            }
            properties.registerCallback(callback, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_FASTEST)
            awaitClose {
                properties.unregisterCallback(callback)
                car.disconnect()
            }
        }
    }

    /** capability 확인 후 지원이면 [create]가 만든 Flow를, 아니면 아무것도 내보내지 않는 공유 Flow. */
    private fun <T> signal(type: SignalType, create: suspend () -> Flow<T>?): Flow<T> = flow {
        val source = create()
        if (source == null) {
            Log.i(TAG, "$type: unsupported")
            return@flow
        }
        supported.update { it + type }
        source.collect { emit(it) }
    }.shareIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), replay = 1)

    private suspend fun checkCapability(
        name: String,
        check: (ok: (Boolean) -> Unit, fail: (Exception) -> Unit) -> Unit,
    ): Boolean = withRetry(name, check).getOrDefault(false)

    /**
     * Vehicle Service 연결은 initialize() 이후 비동기로 완료되고, 조회 실패도 "미지원"처럼 보일 수 있어
     * (Vehicle SDK Sample App 가이드) 몇 번 재시도한다. 끝내 실패하면 마지막 실패를 돌려준다.
     */
    private suspend fun <T> withRetry(
        name: String,
        block: (ok: (T) -> Unit, fail: (Exception) -> Unit) -> Unit,
    ): Result<T> {
        var result = call(block)
        for (attempt in 2..CAPABILITY_ATTEMPTS) {
            if (result.isSuccess) break
            logFailure("$name attempt ${attempt - 1}/$CAPABILITY_ATTEMPTS", result.exceptionOrNull()!!)
            delay(CAPABILITY_RETRY_DELAY_MS)
            result = call(block)
        }
        result.onFailure { logFailure("$name final", it) }
        return result
    }

    private suspend fun <T> call(block: (ok: (T) -> Unit, fail: (Exception) -> Unit) -> Unit): Result<T> =
        withTimeoutOrNull(CALL_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                block(
                    { value -> if (cont.isActive) cont.resume(Result.success(value)) },
                    { e -> if (cont.isActive) cont.resume(Result.failure(e)) },
                )
            }
        } ?: Result.failure(IllegalStateException("timeout after ${CALL_TIMEOUT_MS}ms"))

    private fun logFailure(what: String, e: Throwable) {
        Log.w(TAG, "$what failed: ${e.message}")
    }

    private companion object {
        const val TAG = "PleosSignals"
        const val CAPABILITY_ATTEMPTS = 3
        const val CAPABILITY_RETRY_DELAY_MS = 1_000L
        const val CALL_TIMEOUT_MS = 3_000L
        const val TELLTALE_POLL_MS = 2_000L
        const val STOP_TIMEOUT_MS = 5_000L
        const val NANOS_PER_MILLI = 1_000_000L

        val NON_CABIN_DOORS = setOf(DoorValue.HOOD, DoorValue.REAR)

        val TELLTALE_WARNINGS = mapOf(
            TelltaleType.AIRBAG to SafetyWarning.AIRBAG_TELLTALE,
            TelltaleType.ABS to SafetyWarning.ABS,
            TelltaleType.ESC to SafetyWarning.ESC,
            TelltaleType.MASTER_WARNING to SafetyWarning.MASTER_WARNING,
        )

        fun CapabilityConfig.isReadable() = accessType == AccessType.READ || accessType == AccessType.READ_WRITE

        /** 주간주행등도 켜진 것으로 본다. */
        fun LightState.isOn() = this == LightState.ON || this == LightState.DAYTIME_RUNNING

        fun Telltales.isActive(type: TelltaleType) =
            telltales.any { it.type == type && it.state != TelltaleState.OFF }

        fun VehicleGear?.toGear(): Gear? = when (this) {
            null -> null
            VehicleGear.GEAR_PARK -> Gear.PARK
            VehicleGear.GEAR_REVERSE -> Gear.REVERSE
            VehicleGear.GEAR_NEUTRAL -> Gear.NEUTRAL
            VehicleGear.GEAR_UNKNOWN -> Gear.UNKNOWN
            else -> Gear.DRIVE // GEAR_DRIVE, GEAR_1..9 (전진)
        }
    }
}
