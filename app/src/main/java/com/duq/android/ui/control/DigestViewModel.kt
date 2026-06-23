package com.duq.android.ui.control

import androidx.lifecycle.ViewModel
import com.duq.android.data.DigestInbox
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Раздел 📰 Дайджест: лента выпусков из локального [DigestInbox] (SharedPreferences
 * на устройстве). Дайджесты на сервере НЕ хранятся — приходят пушем от агента
 * (phone_invoke notify category=digest) и оседают только на телефоне.
 */
@HiltViewModel
class DigestViewModel @Inject constructor(
    private val inbox: DigestInbox
) : ViewModel() {
    val items: StateFlow<List<DigestInbox.Item>> = inbox.items
    fun refresh() = inbox.refresh()
    fun clear() = inbox.clear()
}
