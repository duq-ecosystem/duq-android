package com.duq.android.auth

import android.util.Base64
import com.duq.android.data.SettingsRepository
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Ed25519 device identity (software, via BouncyCastle) — matches the OpenClaw
 * gateway contract exactly:
 *   - publicKey: base64url(raw 32-byte Ed25519 public key)
 *   - device.id: SHA256(raw public key).hex
 *   - signature: base64url(raw 64-byte Ed25519 signature)
 *
 * AndroidKeyStore Ed25519 proved unreliable on-device — getCertificate() could
 * return null (NPE) and the produced signatures were rejected by the gateway
 * (DEVICE_AUTH_SIGNATURE_INVALID). Software Ed25519 gives explicit raw bytes
 * that match Node's crypto.sign/verify byte-for-byte (verified against the
 * gateway's own device-identity module). The 32-byte private seed lives in the
 * app's encrypted prefs (SettingsRepository).
 *
 * No @Singleton/@Inject — provided via AppModule.provideDeviceIdentityManager.
 */
class DeviceIdentityManager(
    private val settings: SettingsRepository,
    /** "operator" (chat, default) or "node" (bot→phone commands) — separate keypairs. */
    private val keyName: String = "operator"
) {

    private val priv: Ed25519PrivateKeyParameters by lazy { loadOrCreateKey() }

    private val isNode: Boolean get() = keyName == "node"

    private fun loadOrCreateKey(): Ed25519PrivateKeyParameters {
        val existing = if (isNode) settings.getNodeKeySeed() else settings.getDeviceKeySeed()
        existing?.let { return Ed25519PrivateKeyParameters(it, 0) }
        val seed = ByteArray(Ed25519PrivateKeyParameters.KEY_SIZE).also { SecureRandom().nextBytes(it) }
        if (isNode) settings.saveNodeKeySeed(seed) else settings.saveDeviceKeySeed(seed)
        return Ed25519PrivateKeyParameters(seed, 0)
    }

    /** Raw 32-byte Ed25519 public key. */
    private fun rawPublicKey(): ByteArray = priv.generatePublicKey().encoded

    private fun b64url(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

    /** Raw 32-byte Ed25519 public key, base64url encoded (no padding). */
    fun getPublicKeyBase64Url(): String = b64url(rawPublicKey())

    /** device.id = SHA256(raw public key bytes).hex — derived, NOT stored. */
    fun getDeviceId(): String =
        MessageDigest.getInstance("SHA-256").digest(rawPublicKey())
            .joinToString("") { "%02x".format(it) }

    /** Ed25519 signature over payload, base64url encoded (no padding). */
    fun sign(payload: String): String {
        val msg = payload.toByteArray(Charsets.UTF_8)
        val signer = Ed25519Signer().apply {
            init(true, priv)
            update(msg, 0, msg.size)
        }
        return b64url(signer.generateSignature())
    }
}
