package com.whitemason.okaynow.app.presentation.accident

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** 화면용 창구. 상태와 부수 효과는 앱 전체에서 하나인 [AccidentStore]가 들고 있다(백그라운드 감시와 공유). */
@HiltViewModel
class AccidentViewModel @Inject constructor(private val store: AccidentStore) : ViewModel() {
    val state: StateFlow<AccidentUiState> = store.state
    val uiEffects: SharedFlow<AccidentUiEffect> = store.uiEffects

    fun onIntent(intent: AccidentIntent) = store.dispatch(intent)
}
