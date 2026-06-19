package com.duq.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duq.android.network.openclaw.OpenClawGatewayClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PairingViewModel @Inject constructor(
    private val gatewayClient: OpenClawGatewayClient
) : ViewModel() {

    sealed class PairingStatus {
        object Idle : PairingStatus()
        object Waiting : PairingStatus()
        data class Error(val message: String) : PairingStatus()
        object Success : PairingStatus()
    }

    private val _status = MutableStateFlow<PairingStatus>(PairingStatus.Idle)
    val status: StateFlow<PairingStatus> = _status

    fun pair(gatewayUrl: String, bootstrapToken: String) {
        viewModelScope.launch {
            _status.value = PairingStatus.Waiting
            val success = try {
                gatewayClient.startPairing(gatewayUrl, bootstrapToken)
            } catch (e: Exception) {
                false
            }
            _status.value = if (success) PairingStatus.Success else PairingStatus.Error("Pairing failed or rejected")
        }
    }

    fun reset() { _status.value = PairingStatus.Idle }
}
