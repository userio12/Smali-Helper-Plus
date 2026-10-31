package com.riyadm.smalihelperplus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.riyadm.smalihelperplus.core.content.BundledContentSeeder
import com.riyadm.smalihelperplus.core.datastore.UserPreferences
import com.riyadm.smalihelperplus.core.datastore.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    preferencesRepository: UserPreferencesRepository,
    private val seeder: BundledContentSeeder,
) : ViewModel() {

    /** Null until preferences are loaded; the splash screen stays visible meanwhile. */
    val preferences: StateFlow<UserPreferences?> = preferencesRepository.preferences
        .map<UserPreferences, UserPreferences?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val contentReady: StateFlow<Boolean> = seeder.ready

    init {
        viewModelScope.launch { seeder.seedIfNeeded() }
    }
}
