package com.whitemason.okaynow.app.data.report

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.whitemason.okaynow.app.domain.report.Insurer
import com.whitemason.okaynow.app.domain.report.InsurerRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settings by preferencesDataStore(name = "settings")

/** C0 사전 설정의 보험사(DataStore). 이름과 번호가 다 있어야 등록된 것으로 본다. */
@Singleton
class DataStoreInsurerRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : InsurerRepository {

    override val insurer: Flow<Insurer?> = context.settings.data.map { prefs ->
        val name = prefs[NAME]
        val phone = prefs[PHONE]
        if (name.isNullOrBlank() || phone.isNullOrBlank()) null else Insurer(name, phone)
    }

    override suspend fun save(insurer: Insurer?) {
        context.settings.edit { prefs ->
            if (insurer == null) {
                prefs.remove(NAME); prefs.remove(PHONE)
            } else {
                prefs[NAME] = insurer.name.trim(); prefs[PHONE] = insurer.phone.trim()
            }
        }
    }

    private companion object {
        val NAME = stringPreferencesKey("insurer_name")
        val PHONE = stringPreferencesKey("insurer_phone")
    }
}
