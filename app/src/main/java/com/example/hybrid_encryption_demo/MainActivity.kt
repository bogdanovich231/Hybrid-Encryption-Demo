package com.example.hybrid_encryption_demo

import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.hybrid_encryption_demo.ui.theme.HybridEncryptionDemoTheme
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.*
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HybridEncryptionDemoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }
}

object SimpleCryptoManager {
    private var rsaKeyPair: KeyPair? = null
    private var aesKey: SecretKey? = null

    fun generateRSAKeyPair(): Boolean {
        return try {
            val keyGen = KeyPairGenerator.getInstance("RSA")
            keyGen.initialize(2048)
            rsaKeyPair = keyGen.generateKeyPair()
            println("DEBUG: RSA key pair generated successfully")
            true
        } catch (e: Exception) {
            println("DEBUG: Failed to generate RSA keys: ${e.message}")
            false
        }
    }

    fun getPublicKeyBase64(): String? {
        return rsaKeyPair?.public?.encoded?.let {
            Base64.encodeToString(it, Base64.NO_WRAP)
        }
    }

    fun decryptAesKey(encryptedAesKeyBase64: String): ByteArray? {
        return try {

            val encryptedBytes = Base64.decode(encryptedAesKeyBase64, Base64.NO_WRAP)

            val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            cipher.init(Cipher.DECRYPT_MODE, rsaKeyPair?.private)

            val result = cipher.doFinal(encryptedBytes)

            result
        } catch (e: Exception) {
            println("DEBUG: Decryption failed: ${e.message}")
            null
        }
    }

    fun saveAesKey(keyBytes: ByteArray) {
        aesKey = SecretKeySpec(keyBytes, "AES")
    }

    fun getAesKey(): SecretKey? {
        return aesKey
    }

    fun decryptAesGcm(ciphertextBase64: String): String? {
        return try {
            val combined = Base64.decode(ciphertextBase64, Base64.NO_WRAP)

            val iv = combined.copyOfRange(0, 12)
            val ciphertextWithTag = combined.copyOfRange(12, combined.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, iv)

            cipher.init(Cipher.DECRYPT_MODE, aesKey, spec)
            val decryptedBytes = cipher.doFinal(ciphertextWithTag)

            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            println("DEBUG: AES-GCM decryption failed: ${e.message}")
            null
        }
    }
}

object NetworkManager {
    private val client = OkHttpClient()
    private val gson = Gson()

    private const val BASE_URL = "http://10.0.2.2:3000/api"

    data class KeyRequest(val public_key: String)
    data class KeyResponse(val status: String)
    data class SecretResponse(val encrypted_secret: String)
    data class MessageResponse(val ciphertext: String)

    fun sendPublicKey(publicKey: String, callback: (Boolean, String) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {

                val json = gson.toJson(KeyRequest(publicKey))
                val body = json.toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url("$BASE_URL/register-key")
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    val bodyString = response.body?.string() ?: ""

                    if (response.isSuccessful) {
                        callback(true, "✓ Klucz publiczny wysłany!")
                    } else {
                        callback(false, "Błąd serwera: ${response.code}")
                    }
                }
            } catch (e: Exception) {
                callback(false, "Błąd sieci: ${e.message}")
            }
        }
    }

    fun getEncryptedAesKey(callback: (Boolean, String, String?) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {

                val request = Request.Builder()
                    .url("$BASE_URL/get-secret")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    val bodyString = response.body?.string() ?: ""

                    if (response.isSuccessful) {
                        try {
                            val secretResponse = gson.fromJson(bodyString, SecretResponse::class.java)
                            callback(true, "✓ Otrzymano klucz AES", secretResponse.encrypted_secret)
                        } catch (e: Exception) {
                            callback(false, "Błąd parsowania JSON", null)
                        }
                    } else {
                        callback(false, "Błąd serwera: ${response.code}", null)
                    }
                }
            } catch (e: Exception) {
                callback(false, "Błąd sieci: ${e.message}", null)
            }
        }
    }

    fun getEncryptedMessage(callback: (Boolean, String, String?) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {

                val request = Request.Builder()
                    .url("$BASE_URL/get-message")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    val bodyString = response.body?.string() ?: ""

                    if (response.isSuccessful) {
                        try {
                            val messageResponse = gson.fromJson(bodyString, MessageResponse::class.java)
                            callback(true, "✓ Otrzymano wiadomość", messageResponse.ciphertext)
                        } catch (e: Exception) {
                            callback(false, "Błąd parsowania JSON", null)
                        }
                    } else {
                        callback(false, "Błąd serwera: ${response.code}", null)
                    }
                }
            } catch (e: Exception) {
                callback(false, "Błąd sieci: ${e.message}", null)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    var encryptedText by remember { mutableStateOf("") }
    var decryptedText by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Status: Gotowy") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Hybrydowe Szyfrowanie",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                status = "Generowanie kluczy RSA..."

                val success = SimpleCryptoManager.generateRSAKeyPair()
                if (!success) {
                    status = "Błąd: Nie udało się wygenerować kluczy"
                    return@Button
                }

                val publicKey = SimpleCryptoManager.getPublicKeyBase64()
                if (publicKey == null) {
                    status = "Błąd: Nie udało się uzyskać klucza publicznego"
                    return@Button
                }

                println("DEBUG: Public key (first 50 chars): ${publicKey.take(50)}...")

                status = "Wysyłanie klucza publicznego..."

                NetworkManager.sendPublicKey(publicKey) { isSuccess, message ->
                    status = message
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("1. Generuj i wyślij klucz publiczny")
        }

        Button(
            onClick = {
                if (SimpleCryptoManager.getAesKey() != null) {
                    status = "AES klucz już istnieje"
                    return@Button
                }

                status = "Pobieranie zaszyfrowanego klucza AES..."

                NetworkManager.getEncryptedAesKey { isSuccess, message, encryptedAesKey ->
                    if (isSuccess && encryptedAesKey != null) {
                        status = "Odszyfrowywanie klucza AES..."

                        val decryptedKeyBytes = SimpleCryptoManager.decryptAesKey(encryptedAesKey)
                        if (decryptedKeyBytes != null) {
                            SimpleCryptoManager.saveAesKey(decryptedKeyBytes)
                            status = "$message (${decryptedKeyBytes.size * 8} bitów)"
                        } else {
                            status = "Błąd: Nie udało się odszyfrować klucza AES"
                        }
                    } else {
                        status = message
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("2. Odbierz wspólny sekret AES")
        }

        Button(
            onClick = {
                if (SimpleCryptoManager.getAesKey() == null) {
                    status = "Najpierw wykonaj krok 2!"
                    return@Button
                }

                status = "Pobieranie zaszyfrowanej wiadomości..."

                NetworkManager.getEncryptedMessage { isSuccess, message, ciphertext ->
                    if (isSuccess && ciphertext != null) {
                        encryptedText = ciphertext

                        status = "Odszyfrowywanie wiadomości AES-GCM..."

                        val decrypted = SimpleCryptoManager.decryptAesGcm(ciphertext)
                        if (decrypted != null) {
                            decryptedText = decrypted
                            status = "✓ Wiadomość odszyfrowana!"
                            println("DEBUG: Decrypted message: $decrypted")
                        } else {
                            status = "Błąd: Nie udało się odszyfrować wiadomości"
                        }
                    } else {
                        status = message
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("3. Odbierz i odszyfruj wiadomość")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Zaszyfrowana wiadomość (Base64):",
            style = MaterialTheme.typography.bodyMedium
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            BasicTextField(
                value = encryptedText,
                onValueChange = { encryptedText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .padding(12.dp),
                readOnly = true
            )
        }

        Text(
            text = "Odszyfrowana wiadomość:",
            style = MaterialTheme.typography.bodyMedium
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            BasicTextField(
                value = decryptedText,
                onValueChange = { decryptedText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .padding(12.dp),
                readOnly = true
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    status.startsWith("✓") -> MaterialTheme.colorScheme.primaryContainer
                    status.contains("Błąd") -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            )
        ) {
            Text(
                text = status,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
                color = when {
                    status.startsWith("✓") -> MaterialTheme.colorScheme.onPrimaryContainer
                    status.contains("Błąd") -> MaterialTheme.colorScheme.onErrorContainer
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}