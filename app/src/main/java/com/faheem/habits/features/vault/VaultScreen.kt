@file:Suppress("LongParameterList", "CyclomaticComplexMethod", "TooManyFunctions", "FunctionNaming", "ReturnCount")

package com.faheem.habits.features.vault
import com.faheem.habits.core.data.*
import com.faheem.habits.core.data.supabase.SupabaseRestClient
import com.faheem.habits.core.data.sync.SupabaseSyncRepository
import com.faheem.habits.core.notifications.*
import com.faheem.habits.core.ui.*
import com.faheem.habits.R


import android.app.Activity
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.StickyNote2
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

enum class VaultImportAction(val label: String, val description: String) {
    Insert("Insert", "Add only records that do not already exist"),
    Update("Update", "Replace matching records; ignore new records"),
    Upsert("Upsert", "Add new records and update matching records"),
    Overwrite("Overwrite", "Delete the current vault records, then import")
}

data class VaultRestoreResult(val key: ByteArray, val importedCount: Int)

internal fun uniqueVaultEntries(
    records: List<VaultRecord>,
    key: ByteArray
): List<Pair<VaultRecord, VaultSecret>> {
    val seen = mutableSetOf<VaultSecret>()
    val seenUuids = mutableSetOf<String>()
    return records.mapNotNull { record ->
        val secret = VaultCrypto.decrypt(record.nonce, record.ciphertext, key)
        if (secret !in seen && record.uuid !in seenUuids) {
            seen += secret
            seenUuids += record.uuid
            record to secret
        } else {
            null
        }
    }
}

class VaultViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).vaultDao()
    private val accountKeys = SupabaseRestClient(app)
    private val profilePrefs = app.getSharedPreferences("account_profile", Context.MODE_PRIVATE)
    private val accountId = profilePrefs.getString("user_id", null).orEmpty()
    private val prefs = app.getSharedPreferences(
        "momentum_vault_${accountId.ifBlank { "unlinked" }}",
        Context.MODE_PRIVATE
    )
    private val vmkWrappedPref = "vmk_wrapped_password_v1"
    private val biometricWrappedPref = "vmk_wrapped_biometric_v1"
    private val failedAttemptsPref = "failed_unlock_attempts"
    private val accountLinkedPref = "account_linked_v1"

    fun isConfigured(): Boolean = prefs.contains(VaultCrypto.SALT_PREF) && prefs.contains(VaultCrypto.CHECK_PREF)
    fun needsAccountLink(): Boolean = isConfigured() && !prefs.getBoolean(accountLinkedPref, false)
    fun linkedEmail(): String = profilePrefs.getString("email", "").orEmpty()

    suspend fun setupAccountPassword(masterPassword: String): ByteArray = withContext(Dispatchers.IO) {
        check(!isConfigured()) { "A Vault password is already set on this device." }
        val accountKey = unlockWithAccount()
        try {
            storePasswordWrappedKey(masterPassword, accountKey)
            accountKey
        } catch (failure: Exception) {
            accountKey.fill(0)
            throw failure
        }
    }

    suspend fun unlockWithPassword(masterPassword: String): ByteArray =
        linkExistingVault(masterPassword)

    suspend fun unlockWithAccount(): ByteArray = withContext(Dispatchers.IO) {
        val accountKey = accountKeys.fetchVaultKey()
        if (accountKey != null) {
            SupabaseSyncRepository(getApplication()).syncVaultEntries(accountKey, fullPull = true)
            prefs.edit().putBoolean(accountLinkedPref, true).apply()
            return@withContext accountKey
        }
        if (isConfigured()) {
            error("This device has older encrypted Vault data, but this account has no Vault key. The existing records were left unchanged and cannot be opened by this account-only Vault.")
        }
        check(dao.getAllForSync().isEmpty()) {
            "Encrypted records exist on this device but no account key is saved. Restore a matching backup; do not create a new key."
        }
        check(accountKeys.count("momentum_vault_entries") == 0) {
            "Encrypted account records exist without an account key. A new key cannot decrypt them; restore a matching backup."
        }
        val generatedKey = VaultCrypto.newVmk()
        try {
            accountKeys.saveVaultKeyIfAbsent(generatedKey)
            val savedKey = accountKeys.fetchVaultKey()
                ?: error("The account key was not available after saving.")
            SupabaseSyncRepository(getApplication()).syncVaultEntries(savedKey, fullPull = true)
            prefs.edit().putBoolean(accountLinkedPref, true).apply()
            savedKey
        } finally {
            generatedKey.fill(0)
        }
    }

    suspend fun linkExistingVault(masterPassword: String): ByteArray = withContext(Dispatchers.IO) {
        val localKey = unlock(masterPassword)
            ?: error("That existing Vault password did not unlock this device's vault.")
        var remoteAccountKey: ByteArray? = null
        try {
            val accountKey = accountKeys.fetchVaultKey().also { remoteAccountKey = it }
            if (accountKey != null) {
                if (!java.security.MessageDigest.isEqual(localKey, accountKey)) {
                    migrateReadableRecords(localKey, accountKey)
                }
                SupabaseSyncRepository(getApplication()).syncVaultEntries(accountKey, fullPull = true)
                storePasswordWrappedKey(masterPassword, accountKey)
                prefs.edit().putBoolean(accountLinkedPref, true).apply()
                localKey.fill(0)
                remoteAccountKey = null
                return@withContext accountKey
            } else {
                val localRecords = dao.getAll()
                val readableRecords = localRecords.count { record ->
                    runCatching { VaultCrypto.decrypt(record.nonce, record.ciphertext, localKey) }.isSuccess
                }
                check(readableRecords > 0 || localRecords.isEmpty()) {
                    "The old password unlocked Vault settings, but none of the local records match its key. " +
                        "The signed-in account has no Vault key yet, so the app cannot safely link or replace its encrypted records."
                }
                SupabaseSyncRepository(getApplication()).syncVaultEntries(localKey, fullPull = true)
                check(accountKeys.count("momentum_vault_entries") <= dao.getAllForSync().size) {
                    "The account has Vault records that are not present on this device. Sync them before linking the key."
                }
                accountKeys.saveVaultKeyIfAbsent(localKey)
                val savedKey = accountKeys.fetchVaultKey()
                    ?: error("The account key was not available after saving.")
                check(java.security.MessageDigest.isEqual(localKey, savedKey)) {
                    savedKey.fill(0)
                    "A different Vault key was linked to this account concurrently."
                }
                storePasswordWrappedKey(masterPassword, savedKey)
                savedKey.fill(0)
                SupabaseSyncRepository(getApplication()).syncVaultEntries(localKey, fullPull = true)
                prefs.edit().putBoolean(accountLinkedPref, true).apply()
                localKey
            }
        } catch (failure: Exception) {
            localKey.fill(0)
            remoteAccountKey?.fill(0)
            throw failure
        }
    }

    private suspend fun migrateReadableRecords(oldKey: ByteArray, accountKey: ByteArray): Int {
        var migrated = 0
        dao.getAll().forEach { record ->
            val alreadyAccountEncrypted = runCatching {
                VaultCrypto.decrypt(record.nonce, record.ciphertext, accountKey)
            }.isSuccess
            if (!alreadyAccountEncrypted) {
                val secret = runCatching {
                    VaultCrypto.decrypt(record.nonce, record.ciphertext, oldKey)
                }.getOrNull()
                if (secret != null) {
                    val encrypted = VaultCrypto.encrypt(secret, accountKey)
                    dao.upsert(
                        record.copy(
                            nonce = encrypted.first,
                            ciphertext = encrypted.second,
                            updatedAt = System.currentTimeMillis(),
                            dirty = true,
                            deleted = false
                        )
                    )
                    migrated++
                }
            }
        }
        return migrated
    }

    suspend fun setup(masterPassword: String): ByteArray = withContext(Dispatchers.IO) {
        check(dao.getAll().isEmpty()) {
            "Encrypted vault records already exist, but this device has no matching key setup. Restore a backup made with the original vault password before creating a vault."
        }
        val salt = VaultCrypto.newSalt()
        val passwordChars = masterPassword.toCharArray()
        val key = VaultCrypto.deriveKey(passwordChars, salt)
        passwordChars.fill('\u0000')
        val vmk = VaultCrypto.newVmk()
        val wrapped = VaultCrypto.wrapVmk(vmk, key)
        val saved = prefs.edit()
            .putString(VaultCrypto.SALT_PREF, VaultCrypto.encode(salt))
            .putString(VaultCrypto.CHECK_PREF, VaultCrypto.encode(VaultCrypto.verifier(key)))
            .putString(vmkWrappedPref, encodeWrapped(wrapped))
            .commit()
        if (!saved) {
            key.fill(0)
            vmk.fill(0)
            error("Could not save the vault setup. Please try again.")
        }
        key.fill(0)
        vmk
    }

    suspend fun unlock(masterPassword: String): ByteArray? = withContext(Dispatchers.IO) {
        val failedAttempts = prefs.getInt(failedAttemptsPref, 0)
        if (failedAttempts > 0) {
            Thread.sleep((500L * (1L shl failedAttempts.coerceAtMost(6))).coerceAtMost(30_000L))
        }
        val salt = prefs.getString(VaultCrypto.SALT_PREF, null) ?: return@withContext null
        val check = prefs.getString(VaultCrypto.CHECK_PREF, null) ?: return@withContext null
        val passwordChars = masterPassword.toCharArray()
        val key = try { VaultCrypto.deriveKey(passwordChars, VaultCrypto.decode(salt)) } catch (_: Exception) {
            passwordChars.fill('\u0000')
            return@withContext null
        }
        passwordChars.fill('\u0000')
        val matches = try { VaultCrypto.matches(key, VaultCrypto.decode(check)) } catch (_: Exception) { false }
        if (!matches) {
            prefs.edit().putInt(failedAttemptsPref, (failedAttempts + 1).coerceAtMost(10)).apply()
            key.fill(0)
            return@withContext null
        }
        prefs.edit().remove(failedAttemptsPref).apply()
        val wrapped = prefs.getString(vmkWrappedPref, null)
        if (wrapped != null) {
            return@withContext try {
                val parts = decodeWrapped(wrapped)
                val vmk = VaultCrypto.unwrapVmk(parts.first, parts.second, key)
                key.fill(0)
                vmk
            } catch (_: Exception) {
                key.fill(0)
                null
            }
        }
        migrateLegacy(key, salt)
    }

    suspend fun unlockWithBiometric(cipher: javax.crypto.Cipher): ByteArray? = withContext(Dispatchers.IO) {
        val wrapped = prefs.getString(biometricWrappedPref, null) ?: return@withContext null
        runCatching {
            val parts = decodeWrapped(wrapped)
            VaultCrypto.unwrapVmkWithBiometric(cipher, parts.second)
        }.getOrNull()
    }

    fun prepareBiometricCipher(): javax.crypto.Cipher = VaultCrypto.biometricCipher()

    fun prepareBiometricDecryptCipher(): javax.crypto.Cipher? {
        val wrapped = prefs.getString(biometricWrappedPref, null) ?: return null
        return runCatching { VaultCrypto.biometricDecryptCipher(decodeWrapped(wrapped).first) }.getOrNull()
    }

    fun hasBiometricUnlock(): Boolean = prefs.contains(biometricWrappedPref)

    suspend fun enableBiometric(vmk: ByteArray, cipher: javax.crypto.Cipher) = withContext(Dispatchers.IO) {
        val wrapped = VaultCrypto.wrapVmkWithBiometric(vmk, cipher)
        check(prefs.edit().putString(biometricWrappedPref, encodeWrapped(wrapped)).commit()) {
            "Could not save biometric vault unlock."
        }
    }

    suspend fun changeMasterPassword(vmk: ByteArray, newPassword: String) = withContext(Dispatchers.IO) {
        val salt = VaultCrypto.newSalt()
        val chars = newPassword.toCharArray()
        val key = try {
            VaultCrypto.deriveKey(chars, salt)
        } finally {
            chars.fill('\u0000')
        }
        try {
            val wrapped = VaultCrypto.wrapVmk(vmk, key)
            check(
                prefs.edit()
                    .putString(VaultCrypto.SALT_PREF, VaultCrypto.encode(salt))
                    .putString(VaultCrypto.CHECK_PREF, VaultCrypto.encode(VaultCrypto.verifier(key)))
                    .putString(vmkWrappedPref, encodeWrapped(wrapped))
                    .commit()
            ) { "Could not save the new master password." }
        } finally {
            key.fill(0)
        }
    }

    private suspend fun migrateLegacy(oldKey: ByteArray, salt: String): ByteArray {
        try {
            val records = dao.getAll()
            val backup = JSONArray()
            records.forEach { row ->
                backup.put(
                    JSONObject()
                        .put("id", row.id)
                        .put("nonce", VaultCrypto.encode(row.nonce))
                        .put("ciphertext", VaultCrypto.encode(row.ciphertext))
                        .put("updatedAt", row.updatedAt)
                )
            }
            val backupFile = java.io.File(getApplication<Application>().filesDir, "vault-legacy-migration-backup.json")
            backupFile.writeText(
                JSONObject().put("format", "momentum-vault-legacy-encrypted-backup")
                    .put("version", 1).put("salt", salt).put("entries", backup).toString(),
                Charsets.UTF_8
            )
            val decrypted = records.map { row -> row to VaultCrypto.decrypt(row.nonce, row.ciphertext, oldKey) }
            val vmk = VaultCrypto.newVmk()
            val migrated = decrypted.map { (row, secret) ->
                val encrypted = VaultCrypto.encrypt(secret, vmk)
                row.copy(nonce = encrypted.first, ciphertext = encrypted.second, dirty = true)
            }
            dao.upsertAll(migrated)
            val passwordWrapped = VaultCrypto.wrapVmk(vmk, oldKey)
            check(
                prefs.edit().putString(vmkWrappedPref, encodeWrapped(passwordWrapped)).commit()
            ) { "Could not finish the vault encryption migration." }
            return vmk
        } finally {
            oldKey.fill(0)
        }
    }

    private fun encodeWrapped(value: Pair<ByteArray, ByteArray>): String =
        "${VaultCrypto.encode(value.first)}:${VaultCrypto.encode(value.second)}"

    private fun storePasswordWrappedKey(masterPassword: String, accountKey: ByteArray) {
        val salt = VaultCrypto.newSalt()
        val chars = masterPassword.toCharArray()
        val passwordKey = try {
            VaultCrypto.deriveKey(chars, salt)
        } finally {
            chars.fill('\u0000')
        }
        try {
            val wrapped = VaultCrypto.wrapVmk(accountKey, passwordKey)
            check(
                prefs.edit()
                    .putString(VaultCrypto.SALT_PREF, VaultCrypto.encode(salt))
                    .putString(VaultCrypto.CHECK_PREF, VaultCrypto.encode(VaultCrypto.verifier(passwordKey)))
                    .putString(vmkWrappedPref, encodeWrapped(wrapped))
                    .commit()
            ) { "Could not save the Vault password." }
        } finally {
            passwordKey.fill(0)
        }
    }

    private fun decodeWrapped(value: String): Pair<ByteArray, ByteArray> {
        val parts = value.split(':', limit = 2)
        require(parts.size == 2) { "Invalid vault key wrapper." }
        return VaultCrypto.decode(parts[0]) to VaultCrypto.decode(parts[1])
    }

    suspend fun readAll(key: ByteArray): Pair<List<VaultListItem>, Int> {
        // Snapshot the key before switching threads: the UI may clear its session key while this runs.
        val operationKey = key.copyOf()
        return try {
            withContext(Dispatchers.IO) {
                var unreadable = 0
                val entries = dao.getAll().mapNotNull { record ->
                    try { VaultListItem(record.id, VaultCrypto.decrypt(record.nonce, record.ciphertext, operationKey), record.updatedAt) }
                    catch (_: Exception) { unreadable++; null }
                }
                prefs.edit()
                    .putInt("readable_records", entries.size)
                    .putInt("unreadable_records", unreadable)
                    .apply()
                entries to unreadable
            }
        } finally {
            operationKey.fill(0)
        }
    }

    suspend fun save(id: Int?, secret: VaultSecret, key: ByteArray) {
        // Use an operation-scoped copy so locking the vault cannot zero the key mid-encryption.
        val operationKey = key.copyOf()
        try {
            withContext(Dispatchers.IO) {
                val encrypted = VaultCrypto.encrypt(secret, operationKey)
                val old = id?.let { dao.getById(it) }
                dao.upsert(
                    VaultRecord(
                        id = id ?: 0,
                        nonce = encrypted.first,
                        ciphertext = encrypted.second,
                        updatedAt = System.currentTimeMillis(),
                        uuid = old?.uuid ?: java.util.UUID.randomUUID().toString(),
                        deleted = false,
                        dirty = true,
                        deviceId = old?.deviceId.orEmpty()
                    )
                )
            }
        } finally {
            operationKey.fill(0)
        }
    }

    suspend fun delete(id: Int) = withContext(Dispatchers.IO) {
        dao.markDeleted(id, System.currentTimeMillis())
    }

    suspend fun mergeLegacyRecords(masterPassword: String, accountKey: ByteArray): Int = withContext(Dispatchers.IO) {
        val legacyKey = unlock(masterPassword)
            ?: error("That password did not unlock the old Vault data.")
        try {
            var migrated = 0
            dao.getAll().forEach { record ->
                val alreadyAccountEncrypted = runCatching {
                    VaultCrypto.decrypt(record.nonce, record.ciphertext, accountKey)
                }.isSuccess
                if (!alreadyAccountEncrypted) {
                    val secret = runCatching {
                        VaultCrypto.decrypt(record.nonce, record.ciphertext, legacyKey)
                    }.getOrNull()
                    if (secret != null) {
                        val encrypted = VaultCrypto.encrypt(secret, accountKey)
                        dao.upsert(
                            record.copy(
                                nonce = encrypted.first,
                                ciphertext = encrypted.second,
                                updatedAt = System.currentTimeMillis(),
                                dirty = true,
                                deleted = false
                            )
                        )
                        migrated++
                    }
                }
            }
            if (migrated > 0) SupabaseSyncRepository(getApplication()).syncVaultEntries(accountKey)
            migrated
        } finally {
            legacyKey.fill(0)
        }
    }

    suspend fun resetForQa() = withContext(Dispatchers.IO) {
        SupabaseSyncRepository(getApplication()).resetModule(AppModule.Vault)
        check(
            prefs.edit()
                .clear()
                .putInt("readable_records", 0)
                .putInt("unreadable_records", 0)
                .commit()
        ) { "Vault cloud data was reset, but local Vault key metadata could not be cleared." }
    }

    suspend fun exportEncrypted(destination: Uri, key: ByteArray, backupPassword: String) = withContext(Dispatchers.IO) {
        require(backupPassword.length >= 12) { "Use a backup password with at least 12 characters." }
        val operationKey = key.copyOf()
        try {
            val entries = JSONArray()
            dao.getAll().forEach { row ->
                entries.put(JSONObject().put("id", row.id).put("nonce", VaultCrypto.encode(row.nonce))
                    .put("ciphertext", VaultCrypto.encode(row.ciphertext)).put("updatedAt", row.updatedAt)
                    .put("uuid", row.uuid))
            }
            val payload = JSONObject()
                .put("key", VaultCrypto.encode(operationKey))
                .put("entries", entries)
                .toString().toByteArray(Charsets.UTF_8)
            val passwordChars = backupPassword.toCharArray()
            val protected = try { VaultCrypto.protectBackup(payload, passwordChars) }
            finally { passwordChars.fill('\u0000'); payload.fill(0) }
            val bytes = JSONObject().put("format", "momentum-encrypted-vault")
                .put("version", 3).put("exportedAt", System.currentTimeMillis())
                .put("salt", VaultCrypto.encode(protected.first))
                .put("nonce", VaultCrypto.encode(protected.second))
                .put("payload", VaultCrypto.encode(protected.third))
                .toString(2).toByteArray(Charsets.UTF_8)
            require(bytes.size <= 10 * 1024 * 1024) { "Encrypted vault export exceeds the 10 MB safety limit." }
            val output = getApplication<android.app.Application>().contentResolver.openOutputStream(destination, "w")
                ?: error("Could not open the encrypted export destination.")
            output.use { it.write(bytes) }
        } finally {
            operationKey.fill(0)
        }
    }

    suspend fun importEncrypted(source: Uri, key: ByteArray, action: VaultImportAction): Int = withContext(Dispatchers.IO) {
        val resolver = getApplication<android.app.Application>().contentResolver
        val bytes = resolver.openInputStream(source)?.use { input ->
            input.readBytes().also {
                require(it.size <= 10 * 1024 * 1024) { "Encrypted vault import exceeds the 10 MB safety limit." }
            }
        } ?: error("Could not open the selected vault file.")
        val document = JSONObject(String(bytes, Charsets.UTF_8))
        require(document.optString("format") == "momentum-encrypted-vault") {
            "This is not a Zylo encrypted vault file."
        }
        require(document.optInt("version", -1) in 1..2) { "This vault file version is not supported." }
        val currentSalt = prefs.getString(VaultCrypto.SALT_PREF, null)
            ?: error("Vault is not configured.")
        require(document.optString("salt") == currentSalt) {
            "This backup belongs to a different vault and cannot be imported."
        }
        val entries = document.optJSONArray("entries")
            ?: error("The selected vault file contains no records.")
        val imported = buildList {
            for (index in 0 until entries.length()) {
                val entry = entries.optJSONObject(index)
                    ?: error("The selected vault file contains an invalid record.")
                val record = VaultRecord(
                    id = entry.optInt("id", 0),
                    nonce = VaultCrypto.decode(entry.getString("nonce")),
                    ciphertext = VaultCrypto.decode(entry.getString("ciphertext")),
                    updatedAt = entry.getLong("updatedAt"),
                    uuid = entry.optString("uuid").ifBlank { java.util.UUID.randomUUID().toString() }
                )
                VaultCrypto.decrypt(record.nonce, record.ciphertext, key)
                add(record)
            }
        }
        applyImport(imported, action, key)
    }

    suspend fun restoreEncryptedBackup(source: Uri, masterPassword: String): VaultRestoreResult = withContext(Dispatchers.IO) {
        val resolver = getApplication<Application>().contentResolver
        val bytes = resolver.openInputStream(source)?.use { input ->
            input.readBytes().also {
                require(it.size <= 10 * 1024 * 1024) { "Encrypted vault import exceeds the 10 MB safety limit." }
            }
        } ?: error("Could not open the selected vault file.")
        val document = JSONObject(String(bytes, Charsets.UTF_8))
        require(document.optString("format") == "momentum-encrypted-vault") {
            "This is not a Zylo encrypted vault file."
        }
        val backupVersion = document.optInt("version", -1)
        require(backupVersion in 2..3) { "This encrypted vault backup version is not supported." }
        if (backupVersion == 3) return@withContext restorePasswordProtectedBackup(document, masterPassword)
        val salt = document.getString("salt")
        val chars = masterPassword.toCharArray()
        val passwordKey = try {
            VaultCrypto.deriveKey(chars, VaultCrypto.decode(salt))
        } finally {
            chars.fill('\u0000')
        }
        val vmk = try {
            require(VaultCrypto.matches(passwordKey, VaultCrypto.decode(document.getString("check")))) {
                "The master password does not match this backup."
            }
            val wrapped = decodeWrapped(document.getString("wrappedKey"))
            VaultCrypto.unwrapVmk(wrapped.first, wrapped.second, passwordKey)
        } finally {
            passwordKey.fill(0)
        }
        try {
            val entries = document.optJSONArray("entries")
                ?: error("The selected vault file contains no records.")
            val records = buildList {
                for (index in 0 until entries.length()) {
                    val entry = entries.optJSONObject(index)
                        ?: error("The selected vault file contains an invalid record.")
                    val record = VaultRecord(
                        id = entry.optInt("id", 0),
                        nonce = VaultCrypto.decode(entry.getString("nonce")),
                        ciphertext = VaultCrypto.decode(entry.getString("ciphertext")),
                        updatedAt = entry.getLong("updatedAt"),
                        uuid = entry.optString("uuid").ifBlank { java.util.UUID.randomUUID().toString() }
                    )
                    VaultCrypto.decrypt(record.nonce, record.ciphertext, vmk)
                    add(record)
                }
            }
            val wrappedKey = document.getString("wrappedKey")
            var linkedKey = accountKeys.fetchVaultKey()
            if (linkedKey == null) {
                accountKeys.saveVaultKeyIfAbsent(vmk)
                linkedKey = accountKeys.fetchVaultKey()
            }
            val savedKey = linkedKey ?: error("The account Vault key was not available after restore.")
            check(java.security.MessageDigest.isEqual(vmk, savedKey)) {
                savedKey.fill(0)
                "A different Vault key is already linked to this account."
            }
            savedKey.fill(0)
            check(
                prefs.edit()
                    .putString(VaultCrypto.SALT_PREF, salt)
                    .putString(VaultCrypto.CHECK_PREF, document.getString("check"))
                    .putString(vmkWrappedPref, wrappedKey)
                    .remove(biometricWrappedPref)
                    .remove(failedAttemptsPref)
                    .putBoolean(accountLinkedPref, true)
                    .commit()
            ) { "Could not save the restored vault setup." }
            val importedCount = applyImport(records, VaultImportAction.Upsert, vmk)
            SupabaseSyncRepository(getApplication()).syncVaultEntries(vmk)
            VaultRestoreResult(vmk, importedCount)
        } catch (failure: Exception) {
            vmk.fill(0)
            throw failure
        }
    }

    private suspend fun restorePasswordProtectedBackup(
        document: JSONObject,
        backupPassword: String
    ): VaultRestoreResult {
        val passwordChars = backupPassword.toCharArray()
        val plaintext = try {
            VaultCrypto.openBackup(
                VaultCrypto.decode(document.getString("salt")),
                VaultCrypto.decode(document.getString("nonce")),
                VaultCrypto.decode(document.getString("payload")),
                passwordChars
            )
        } finally {
            passwordChars.fill('\u0000')
        }
        var restoredKey: ByteArray? = null
        try {
            val payload = JSONObject(String(plaintext, Charsets.UTF_8))
            val key = VaultCrypto.decode(payload.getString("key"))
            restoredKey = key
            val entries = payload.getJSONArray("entries")
            val records = buildList {
                for (index in 0 until entries.length()) {
                    val entry = entries.optJSONObject(index)
                        ?: error("The selected vault file contains an invalid record.")
                    val record = VaultRecord(
                        id = entry.optInt("id", 0),
                        nonce = VaultCrypto.decode(entry.getString("nonce")),
                        ciphertext = VaultCrypto.decode(entry.getString("ciphertext")),
                        updatedAt = entry.getLong("updatedAt"),
                        uuid = entry.optString("uuid").ifBlank { java.util.UUID.randomUUID().toString() }
                    )
                    VaultCrypto.decrypt(record.nonce, record.ciphertext, key)
                    add(record)
                }
            }
            var remoteKey = accountKeys.fetchVaultKey()
            if (remoteKey == null) {
                accountKeys.saveVaultKeyIfAbsent(key)
                remoteKey = accountKeys.fetchVaultKey()
            }
            val savedKey = remoteKey ?: error("The account Vault key was not available after restore.")
            val matches = java.security.MessageDigest.isEqual(key, savedKey)
            savedKey.fill(0)
            check(matches) { "A different Vault key is already linked to this account." }
            val importedCount = applyImport(records, VaultImportAction.Upsert, key)
            SupabaseSyncRepository(getApplication()).syncVaultEntries(key)
            check(prefs.edit().putBoolean(accountLinkedPref, true).commit()) {
                "Vault data was restored, but account-link status could not be saved."
            }
            return VaultRestoreResult(key, importedCount)
        } catch (failure: Exception) {
            restoredKey?.fill(0)
            throw failure
        } finally {
            plaintext.fill(0)
        }
    }

    suspend fun importCsv(source: Uri, key: ByteArray, action: VaultImportAction): Int = withContext(Dispatchers.IO) {
        val resolver = getApplication<android.app.Application>().contentResolver
        val text = resolver.openInputStream(source)?.use { input ->
            input.readBytes().also {
                require(it.size <= 10 * 1024 * 1024) { "CSV vault import exceeds the 10 MB safety limit." }
            }
        }?.toString(Charsets.UTF_8)?.removePrefix("\uFEFF")
            ?: error("Could not open the selected CSV file.")
        val rows = parseCsv(text)
        require(rows.size >= 2) { "The CSV file must contain a header row and at least one record." }
        val headers = rows.first().map { it.trim().lowercase(Locale.ROOT) }
        fun value(row: List<String>, vararg names: String): String =
            names.firstNotNullOfOrNull { name ->
                headers.indexOf(name).takeIf { it >= 0 }?.let { row.getOrNull(it)?.trim() }
            }.orEmpty()
        val records = rows.drop(1).mapNotNull { row ->
            val title = value(row, "title", "name", "item", "label")
            val username = value(row, "username", "user", "login username", "login_username", "email")
            val password = value(row, "password", "pass", "login password", "login_password")
            val website = value(row, "website", "url", "login uri", "login_uri", "uri")
            val notes = value(row, "notes", "note", "comments")
            if (title.isBlank() && username.isBlank() && password.isBlank()) null
            else VaultRecord(
                nonce = ByteArray(0),
                ciphertext = ByteArray(0),
                updatedAt = System.currentTimeMillis()
            ).let {
                val encrypted = VaultCrypto.encrypt(
                    VaultSecret(
                        title = title.ifBlank { "Imported item" },
                        type = "login",
                        username = username,
                        password = password,
                        website = website,
                        notes = notes,
                        category = inferredVaultCategory(
                            VaultSecret(title, "login", username, password, website, notes)
                        )
                    ),
                    key
                )
                it.copy(nonce = encrypted.first, ciphertext = encrypted.second)
            }
        }
        require(records.isNotEmpty()) { "No usable records were found in the CSV file." }
        applyImport(records, action, key)
    }

    private suspend fun applyImport(records: List<VaultRecord>, action: VaultImportAction, key: ByteArray): Int {
        val active = dao.getAll()
        val secretsByUuid = active.mapNotNull { record ->
            runCatching { record.uuid to VaultCrypto.decrypt(record.nonce, record.ciphertext, key) }.getOrNull()
        }.toMap()
        val secrets = secretsByUuid.toMutableMap()
        val uniqueIncoming = uniqueVaultEntries(records, key)
        var changed = 0
        when (action) {
            VaultImportAction.Insert -> uniqueIncoming.forEach { (incoming, secret) ->
                val duplicate = dao.getByUuid(incoming.uuid) != null || secrets.values.contains(secret)
                if (!duplicate) {
                    val result = dao.insertImported(incoming.copy(id = 0, dirty = true, deleted = false))
                    if (result != -1L) {
                        changed++
                        secrets[incoming.uuid] = secret
                    }
                }
            }
            VaultImportAction.Update, VaultImportAction.Upsert -> uniqueIncoming.forEach { (incoming, secret) ->
                val existing = dao.getByUuid(incoming.uuid)
                    ?: secrets.entries.firstOrNull { it.value == secret }?.key?.let { dao.getByUuid(it) }
                if (existing != null || action == VaultImportAction.Upsert) {
                    dao.upsert(
                        incoming.copy(
                            id = existing?.id ?: 0,
                            uuid = existing?.uuid ?: incoming.uuid,
                            updatedAt = System.currentTimeMillis(),
                            dirty = true,
                            deleted = false
                        )
                    )
                    changed++
                    secrets[existing?.uuid ?: incoming.uuid] = secret
                }
            }
            VaultImportAction.Overwrite -> {
                dao.markAllDeleted(System.currentTimeMillis())
                uniqueIncoming.forEach { (incoming, secret) ->
                    dao.upsert(incoming.copy(id = 0, updatedAt = System.currentTimeMillis(), dirty = true, deleted = false))
                    changed++
                }
            }
        }
        return changed
    }

    private fun parseCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var index = 0
        fun finishField() {
            row += field.toString()
            field.clear()
        }
        fun finishRow() {
            finishField()
            if (row.any { it.isNotBlank() }) rows += row
            row = mutableListOf()
        }
        while (index < text.length) {
            val character = text[index]
            when {
                character == '"' && quoted && index + 1 < text.length && text[index + 1] == '"' -> {
                    field.append('"')
                    index++
                }
                character == '"' -> quoted = !quoted
                character == ',' && !quoted -> finishField()
                character == '\n' && !quoted -> finishRow()
                character == '\r' && !quoted -> Unit
                else -> field.append(character)
            }
            index++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) finishRow()
        require(!quoted) { "The CSV file contains an unfinished quoted field." }
        return rows
    }
}

@Composable
fun VaultScreen(vm: VaultViewModel = viewModel()) {
    val context = LocalContext.current
    var configured by remember { mutableStateOf(vm.isConfigured()) }
    var sessionKey by remember { mutableStateOf<ByteArray?>(null) }
    var sessionEpoch by remember { mutableIntStateOf(0) }
    var items by remember { mutableStateOf<List<VaultListItem>>(emptyList()) }
    var unreadable by remember { mutableIntStateOf(0) }
    var refresh by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<VaultListItem?>(null) }
    var selected by remember { mutableStateOf<VaultListItem?>(null) }
    var deleteTarget by remember { mutableStateOf<VaultListItem?>(null) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    var pendingImport by remember { mutableStateOf<Pair<Uri, Boolean>?>(null) }
    var pendingRecovery by remember { mutableStateOf<Uri?>(null) }
    var pendingBackupExport by remember { mutableStateOf<Uri?>(null) }
    var backupPassword by remember { mutableStateOf("") }
    var backupPasswordConfirm by remember { mutableStateOf("") }
    var pendingLegacyMigration by remember { mutableStateOf(false) }
    var pendingRecordMerge by remember { mutableStateOf(false) }
    var confirmQaReset by remember { mutableStateOf(false) }
    var recoveryPassword by remember { mutableStateOf("") }
    var legacyPassword by remember { mutableStateOf("") }
    var externalFilePickerOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        busy = true
        try {
            sessionKey = vm.unlockWithAccount()
            configured = true
            sessionEpoch++
        } catch (e: Exception) {
            error = e.message ?: "Could not open Vault with this account."
        } finally {
            busy = false
        }
    }
    val vaultExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        externalFilePickerOpen = false
        if (uri != null && sessionKey != null) {
            pendingBackupExport = uri
            backupPassword = ""
            backupPasswordConfirm = ""
        }
    }
    val vaultImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        externalFilePickerOpen = false
        if (uri != null && sessionKey != null) {
            pendingImport = uri to true
        }
    }
    val csvImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        externalFilePickerOpen = false
        if (uri != null) {
            pendingImport = uri to false
        }
    }
    val recoveryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        externalFilePickerOpen = false
        pendingRecovery = uri
        recoveryPassword = ""
    }
    pendingBackupExport?.let { uri ->
        MomentumDialog(
            onDismissRequest = {
                pendingBackupExport = null
                backupPassword = ""
                backupPasswordConfirm = ""
            },
            title = { Text("Protect encrypted backup") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Choose a backup password with at least 12 characters. You will need it to restore this file on any device.")
                    MomentumTextField(
                        value = backupPassword,
                        onValueChange = { backupPassword = it.take(128) },
                        label = { Text("Backup password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                    MomentumTextField(
                        value = backupPasswordConfirm,
                        onValueChange = { backupPasswordConfirm = it.take(128) },
                        label = { Text("Confirm backup password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy && backupPassword.length >= 12 && backupPassword == backupPasswordConfirm,
                    onClick = {
                        val password = backupPassword
                        val key = sessionKey?.copyOf() ?: return@TextButton
                        pendingBackupExport = null
                        backupPassword = ""
                        backupPasswordConfirm = ""
                        scope.launch {
                            busy = true
                            exportMessage = try {
                                vm.exportEncrypted(uri, key, password)
                                "Encrypted backup saved. Keep the file and password somewhere safe; neither can be recovered if lost."
                            } catch (e: Exception) {
                                "Encrypted vault export failed: ${e.message ?: "unknown error"}"
                            } finally {
                                key.fill(0)
                                busy = false
                            }
                        }
                    }
                ) { Text(if (busy) "Exporting…" else "Save encrypted backup") }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingBackupExport = null
                    backupPassword = ""
                    backupPasswordConfirm = ""
                }) { Text("Cancel") }
            }
        )
    }
    pendingImport?.let { (uri, encrypted) ->
        MomentumDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Import action") },
            text = { Text("Choose how the selected vault records should be imported.") },
            confirmButton = {
                Column {
                    VaultImportAction.entries.forEach { action ->
                        TextButton(onClick = {
                            pendingImport = null
                            val key = sessionKey ?: return@TextButton
                            scope.launch {
                                busy = true
                                exportMessage = try {
                                    val count = if (encrypted) {
                                        vm.importEncrypted(uri, key, action)
                                    } else {
                                        vm.importCsv(uri, key, action)
                                    }
                                    refresh++
                                    "${action.label}: imported $count vault record${if (count == 1) "" else "s"}."
                                } catch (e: Exception) {
                                    "Vault import failed: ${e.message ?: "unknown error"}"
                                } finally { busy = false }
                            }
                        }, modifier = Modifier.fillMaxWidth()) {
                            Column(horizontalAlignment = Alignment.Start) {
                                Text(action.label)
                                Text(action.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Cancel") } }
        )
    }
    pendingRecovery?.let { uri ->
        MomentumDialog(
            onDismissRequest = {
                pendingRecovery = null
                recoveryPassword = ""
            },
            title = { Text("Restore encrypted vault") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Enter the backup password. For older version 2 files, use the Vault master password used when that backup was created.")
                    MomentumTextField(
                        value = recoveryPassword,
                        onValueChange = { recoveryPassword = it.take(128); error = null },
                        label = { Text("Backup password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy && recoveryPassword.isNotEmpty(),
                    onClick = {
                        val password = recoveryPassword
                        recoveryPassword = ""
                        pendingRecovery = null
                        scope.launch {
                            busy = true
                            error = null
                            try {
                                val restored = vm.restoreEncryptedBackup(uri, password)
                                configured = true
                                sessionEpoch++
                                sessionKey?.fill(0)
                                sessionKey = restored.key
                                refresh++
                                exportMessage = "Vault restored: ${restored.importedCount} record${if (restored.importedCount == 1) "" else "s"}."
                            } catch (e: Exception) {
                                val message = "Vault restore failed: ${e.message ?: "unknown error"}"
                                if (configured) exportMessage = message else error = message
                            } finally {
                                busy = false
                            }
                        }
                    }
                ) { Text(if (busy) "Restoring…" else "Restore") }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingRecovery = null
                    recoveryPassword = ""
                }) { Text("Cancel") }
            }
        )
    }
    if (pendingLegacyMigration) {
        MomentumDialog(
            onDismissRequest = {
                pendingLegacyMigration = false
                legacyPassword = ""
            },
            title = { Text("Link existing vault") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Enter the existing Vault password once. After linking, your signed-in account will unlock Vault on this and other devices.")
                    MomentumTextField(
                        value = legacyPassword,
                        onValueChange = { legacyPassword = it.take(128); error = null },
                        label = { Text("Existing Vault password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy && legacyPassword.isNotEmpty(),
                    onClick = {
                        val password = legacyPassword
                        legacyPassword = ""
                        pendingLegacyMigration = false
                        scope.launch {
                            busy = true
                            error = null
                            try {
                                sessionKey = vm.linkExistingVault(password)
                                configured = true
                                sessionEpoch++
                            } catch (e: Exception) {
                                error = e.message ?: "Could not link the existing Vault."
                            } finally {
                                busy = false
                            }
                        }
                    }
                ) { Text(if (busy) "Linking…" else "Link account") }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingLegacyMigration = false
                    legacyPassword = ""
                }) { Text("Cancel") }
            }
        )
    }
    if (pendingRecordMerge) {
        MomentumDialog(
            onDismissRequest = {
                pendingRecordMerge = false
                legacyPassword = ""
            },
            title = { Text("Unlock old Vault records") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Enter the old Vault password once. Any records it can open are re-encrypted with your account key. Everything else is preserved unchanged.")
                    MomentumTextField(
                        value = legacyPassword,
                        onValueChange = { legacyPassword = it.take(128); error = null },
                        label = { Text("Old Vault password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy && legacyPassword.isNotEmpty(),
                    onClick = {
                        val password = legacyPassword
                        legacyPassword = ""
                        pendingRecordMerge = false
                        val activeKey = sessionKey ?: return@TextButton
                        scope.launch {
                            busy = true
                            try {
                                val count = vm.mergeLegacyRecords(password, activeKey)
                                refresh++
                                exportMessage = if (count > 0) {
                                    "Recovered $count old Vault record${if (count == 1) "" else "s"}."
                                } else {
                                    "No records could be opened with that password. Encrypted records were left unchanged."
                                }
                            } catch (e: Exception) {
                                exportMessage = e.message ?: "Could not unlock old Vault records."
                            } finally {
                                busy = false
                            }
                        }
                    }
                ) { Text(if (busy) "Unlocking…" else "Unlock records") }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingRecordMerge = false
                    legacyPassword = ""
                }) { Text("Cancel") }
            }
        )
    }
    if (confirmQaReset) {
        MomentumDialog(
            onDismissRequest = { if (!busy) confirmQaReset = false },
            title = { Text("Reset Vault QA data?") },
            text = {
                Text(
                    "This permanently deletes all Vault records and the Vault key for the signed-in account, " +
                        "plus Vault data and key settings on this device. Other Supabase modules will not be changed. " +
                        "This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            error = null
                            try {
                                vm.resetForQa()
                                sessionKey?.fill(0)
                                sessionKey = null
                                items = emptyList()
                                unreadable = 0
                                configured = false
                                selected = null
                                editing = null
                                deleteTarget = null
                                refresh++
                                exportMessage = "Vault QA data was cleared for this account. Other modules were not changed."
                                confirmQaReset = false
                            } catch (e: Exception) {
                                error = e.message ?: "Could not reset Vault QA data."
                            } finally {
                                busy = false
                            }
                        }
                    }
                ) { Text(if (busy) "Clearing…" else "Delete Vault data", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { confirmQaReset = false }) { Text("Cancel") }
            }
        )
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (externalFilePickerOpen) return@LifecycleEventEffect
        sessionEpoch++
        sessionKey?.fill(0)
        sessionKey = null
        items = emptyList()
        selected = null
        editorOpen = false
        editing = null
        deleteTarget = null
    }
    LaunchedEffect(sessionKey, refresh, sessionEpoch) {
        val key = sessionKey
        val epoch = sessionEpoch
        if (key != null) {
            val result = vm.readAll(key)
            // Ignore results from an earlier session if the vault was locked during the read.
            if (sessionKey === key && sessionEpoch == epoch) {
                items = result.first
                unreadable = result.second
            }
        }
    }

    fun unlockWithAccount() {
        scope.launch {
            busy = true
            error = null
            try {
                sessionKey = vm.unlockWithAccount()
                sessionEpoch++
                configured = true
            } catch (e: Exception) {
                error = e.message ?: "Could not open Vault with this account."
            } finally { busy = false }
        }
    }

    val key = sessionKey
    if (key == null) {
        VaultGate(
            busy = busy,
            error = error,
            linkedEmail = vm.linkedEmail(),
            legacyVaultAvailable = vm.needsAccountLink(),
            onSubmit = ::unlockWithAccount,
            onLinkExisting = { pendingLegacyMigration = true }
        )
    } else {
        VaultHome(
            items = items,
            unreadable = unreadable,
            linkedEmail = vm.linkedEmail(),
            legacyBackupAvailable = vm.isConfigured(),
            onRecoverOldRecords = { legacyPassword = ""; pendingRecordMerge = true },
            onLock = {
                sessionEpoch++
                key.fill(0); sessionKey = null; items = emptyList(); selected = null
                editorOpen = false; editing = null; deleteTarget = null
            },
            onAdd = { editing = null; editorOpen = true },
            onSelect = { selected = it },
            onExport = {
                externalFilePickerOpen = true
                vaultExportLauncher.launch("momentum-vault-encrypted.json")
            },
            onImport = {
                externalFilePickerOpen = true
                vaultImportLauncher.launch(arrayOf("application/json", "text/json", "text/plain"))
            },
            onImportCsv = {
                externalFilePickerOpen = true
                csvImportLauncher.launch(arrayOf("*/*"))
            },
            onResetQaData = { confirmQaReset = true },
            onRestoreBackup = {
                externalFilePickerOpen = true
                recoveryLauncher.launch(arrayOf("application/json", "text/json", "text/plain"))
            }
        )
        exportMessage?.let { Text(it, Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.primary, fontSize = 12.sp) }
    }

    if (editorOpen && key != null) {
        VaultEntryEditor(initial = editing, onDismiss = { editorOpen = false; editing = null }) { secret ->
            scope.launch {
                busy = true
                try {
                    vm.save(editing?.id, secret, key)
                    refresh++
                    editorOpen = false
                    editing = null
                    selected = null
                } catch (e: Exception) { error = e.message ?: "Could not save this item." }
                finally { busy = false }
            }
        }
    }
    selected?.let { item ->
        if (key != null) VaultDetailSheet(item, onDismiss = { selected = null }, onEdit = { editing = item; editorOpen = true; selected = null }, onDelete = { deleteTarget = item; selected = null })
    }
    deleteTarget?.let { item ->
        MomentumDialog(
            onDismissRequest = { deleteTarget = null }, title = { Text("Delete secure item?") },
            text = { Text("${item.secret.title} will be permanently removed from this vault.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { vm.delete(item.id); refresh++; deleteTarget = null }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun VaultGate(
    busy: Boolean,
    error: String?,
    linkedEmail: String,
    legacyVaultAvailable: Boolean,
    onSubmit: () -> Unit,
    onLinkExisting: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = MomentumTokens.screenPadding, vertical = 16.dp), verticalArrangement = Arrangement.Center) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                HeroCard {
                    Icon(Icons.Rounded.Lock, null, Modifier.size(38.dp), tint = Color.White)
                    Spacer(Modifier.height(12.dp))
                    if (linkedEmail.isNotBlank()) {
                        Text(
                            linkedEmail,
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = .84f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        "Open your account Vault",
                        fontSize = 25.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp
                    )
                    Text(
                        "Your signed-in account unlocks your Vault across devices. Anyone with access to your account can also access Vault.",
                        fontSize = 12.5.sp,
                        color = Color.White.copy(alpha = .9f)
                    )
                }
            }
            if (legacyVaultAvailable) {
                item {
                    InfoBanner(
                        icon = Icons.Rounded.Security,
                        text = "This device has an older password-protected Vault. Link it to your account once using its existing password.",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    // Existing-password linking is not part of the account-only Vault flow.
                }
            }
            if (error != null) item {
                InfoBanner(icon = Icons.Rounded.Warning, text = error, tint = MaterialTheme.colorScheme.error)
            }
            item {
                PrimaryButton(
                    text = if (busy) "Opening…" else "Continue with account",
                    onClick = onSubmit,
                    enabled = !busy,
                    icon = Icons.Rounded.LockOpen
                )
            }
            item {
                Text(
                    "Vault locks when Zylo goes into the background. Screenshots are blocked while it is open.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun VaultHome(
    items: List<VaultListItem>,
    unreadable: Int,
    linkedEmail: String,
    legacyBackupAvailable: Boolean,
    onRecoverOldRecords: () -> Unit,
    onLock: () -> Unit,
    onAdd: () -> Unit,
    onSelect: (VaultListItem) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onImportCsv: () -> Unit,
    onResetQaData: () -> Unit,
    onRestoreBackup: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("All") }
    var category by remember { mutableStateOf("All") }
    var favoritesOnly by remember { mutableStateOf(false) }
    var attentionOnly by remember { mutableStateOf(false) }
    var sort by remember { mutableStateOf("Recent") }
    val filtered = items.filter { item ->
        (type == "All" || (type == "Logins" && item.secret.type == "login") || (type == "Secure notes" && item.secret.type == "note")) &&
            (category == "All" || inferredVaultCategory(item.secret) == category) &&
            (!favoritesOnly || item.secret.favorite) &&
            (!attentionOnly || needsVaultAttention(item, items)) &&
            (query.isBlank() || listOf(item.secret.title, item.secret.username, item.secret.website, item.secret.notes).any { it.contains(query, true) })
    }.let { matching ->
        when (sort) {
            "Name" -> matching.sortedBy { it.secret.title.lowercase() }
            "Favorites" -> matching.sortedWith(compareByDescending<VaultListItem> { it.secret.favorite }.thenBy { it.secret.title.lowercase() })
            else -> matching.sortedByDescending { it.updatedAt }
        }
    }
    val health = vaultHealthReport(items)
    val logins = items.count { it.secret.type != "note" }
    val notes = items.size - logins
    val typeSlices = buildList {
        if (logins > 0) add(ChartSlice("Logins", logins.toFloat(), chartColor(0)))
        if (notes > 0) add(ChartSlice("Secure notes", notes.toFloat(), chartColor(1)))
    }
    val healthSlices = if (logins > 0) {
        val issues = health.weak + health.reused + health.duplicates
        val strong = (logins - issues).coerceAtLeast(0)
        buildList {
            if (strong > 0) add(ChartSlice("Healthy", strong.toFloat(), chartColor(3)))
            if (health.weak > 0) add(ChartSlice("Weak", health.weak.toFloat(), chartColor(4)))
            if (health.reused > 0) add(ChartSlice("Reused", health.reused.toFloat(), chartColor(2)))
            if (health.duplicates > 0) add(ChartSlice("Duplicate", health.duplicates.toFloat(), chartColor(6)))
        }
    } else {
        emptyList()
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(MomentumTokens.screenPadding, 6.dp, MomentumTokens.screenPadding, 100.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                HeroCard(
                    modifier = Modifier.padding(horizontal = 2.dp).height(118.dp),
                    contentPadding = 10.dp
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (linkedEmail.isNotBlank()) linkedEmail else "Your account vault",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = .84f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text("${items.size} secure item${if (items.size == 1) "" else "s"}", fontSize = 25.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
                            if (unreadable > 0) {
                                Text("+ $unreadable from an older key", fontSize = 12.sp, color = Color.White.copy(alpha = .8f))
                            }
                        }
                        IconButton(onClick = onLock, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Rounded.Lock, "Lock vault now", tint = Color.White)
                        }
                    }
                }
            }
            if (unreadable > 0) item {
                InfoBanner(
                    icon = Icons.Rounded.Security,
                    text = "$unreadable older record${if (unreadable == 1) "" else "s"} were saved with a previous vault key and are safely preserved. Open them once with the old password, or restore the matching backup.",
                    tint = MaterialTheme.colorScheme.tertiary
                )
                if (legacyBackupAvailable) {
                    // Older-key recovery is no longer offered in the account-only Vault flow.
                } else {
                    // Backup recovery is no longer offered in the account-only Vault flow.
                }
            }
            item {
                val issues = health.weak + health.reused + health.duplicates
                InfoBanner(
                    icon = if (issues > 0) Icons.Rounded.Warning else Icons.Rounded.VerifiedUser,
                    text = if (issues > 0) {
                        "Password health: ${health.weak} weak · ${health.reused} reused · ${health.duplicates} duplicate"
                    } else {
                        "Password health looks good. No weak, reused or duplicate passwords found."
                    },
                    tint = if (issues > 0) MaterialTheme.colorScheme.tertiary else Positive
                )
            }
            if (items.isNotEmpty()) item {
                PremiumCard(contentPadding = PaddingValues(16.dp)) {
                    Text("Vault mix", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(
                        "$logins login${if (logins == 1) "" else "s"} · $notes secure note${if (notes == 1) "" else "s"}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DonutChart(
                            typeSlices,
                            Modifier.weight(1f),
                            centerValue = items.size.toString(),
                            centerLabel = "items"
                        )
                        ChartLegend(typeSlices, Modifier.weight(1.1f))
                    }
                }
            }
            if (healthSlices.isNotEmpty()) item {
                PremiumCard(contentPadding = PaddingValues(16.dp)) {
                    Text("Password health", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(
                        "How saved logins score across weak, reused and duplicate checks.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    CompositionBar(healthSlices) { it.toInt().toString() }
                }
            }
            item {
                MomentumTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("Search passwords and notes") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Rounded.Clear, "Clear search")
                            }
                        }
                    }
                )
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("All", "Logins", "Secure notes").forEach { label ->
                        MomentumChip(selected = type == label, onClick = { type = label }, label = label)
                    }
                }
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    vaultCategories.forEach { label ->
                        MomentumChip(selected = category == label, onClick = { category = label }, label = label)
                    }
                }
            }
            item {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MomentumChip(
                        selected = favoritesOnly,
                        onClick = { favoritesOnly = !favoritesOnly },
                        label = "Favorites",
                        icon = Icons.Rounded.Star
                    )
                    MomentumChip(
                        selected = attentionOnly,
                        onClick = { attentionOnly = !attentionOnly },
                        label = "Needs attention",
                        icon = Icons.Rounded.Warning
                    )
                    Text("Sort", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    listOf("Recent", "Name", "Favorites").forEach { option ->
                        MomentumChip(selected = sort == option, onClick = { sort = option }, label = option)
                    }
                }
            }
            item { SectionHeader(if (type == "All") "Your items" else type, subtitle = "${filtered.size} item${if (filtered.size == 1) "" else "s"}") }
            if (filtered.isEmpty()) item {
                EmptyState(Icons.Rounded.Password, if (items.isEmpty()) "Your vault is empty" else "No matching items", if (items.isEmpty()) "Add a login or secure note to get started" else "Try another search or filter")
            }
            items(filtered, key = { it.id }) { entry -> VaultItemCard(entry) { onSelect(entry) } }
            item {
                Text("Credentials stay encrypted in the database. Copying a value clears the clipboard after 30 seconds when possible.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
            }
            item {
                // Encrypted export is disabled; account sync is the Vault backup path.
                Text(
                    "The backup is protected with a password you choose. Keep both the file and password safe.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                // Backup recovery is no longer offered in the account-only Vault flow.
                Text(
                    "Restore a version 3 backup with its backup password. Older version 2 backups use the original Vault password.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (legacyBackupAvailable) item {
                // Encrypted-file import is no longer offered in the account-only Vault flow.
            }
            item {
                SecondaryButton("Import CSV file", onClick = onImportCsv, icon = Icons.Rounded.TableView)
                Text(
                    "Excel files must be saved as CSV before importing.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                TextButton(onClick = onResetQaData, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.DeleteForever, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text("Reset Vault QA data", color = MaterialTheme.colorScheme.error)
                }
            }
        }
        MomentumFab(
            text = "Add secure item",
            onClick = onAdd,
            modifier = Modifier.align(Alignment.BottomEnd).padding(MomentumTokens.screenPadding)
        )
    }
}

@Composable
private fun VaultItemCard(item: VaultListItem, onClick: () -> Unit) {
    val secret = item.secret
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(
                if (secret.type == "note") Icons.AutoMirrored.Rounded.StickyNote2 else Icons.Rounded.Key,
                MaterialTheme.colorScheme.primary,
                40.dp
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    secret.title.ifBlank { "Untitled item" },
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (secret.type == "note") "Secure note"
                    else secret.username.ifBlank { secret.website.ifBlank { "Login" } },
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (secret.favorite) {
                Icon(
                    Icons.Rounded.Star,
                    "Favorite",
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
            }
            Icon(Icons.Rounded.ChevronRight, "Open ${secret.title}", tint = MaterialTheme.colorScheme.outline)
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 54.dp, top = 12.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
        )
    }
}

@Composable
private fun VaultDetailSheet(item: VaultListItem, onDismiss: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    val secret = item.secret
    var reveal by remember(item.id) { mutableStateOf(false) }
    FormSheet(secret.title, onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(if (secret.type == "note") Icons.AutoMirrored.Rounded.StickyNote2 else Icons.Rounded.Key, MaterialTheme.colorScheme.primary, 46.dp)
            Spacer(Modifier.width(10.dp))
            Column { Text(if (secret.type == "note") "Secure note" else "Login", fontWeight = FontWeight.SemiBold); Text("Updated ${DateTimeFormatter.ofPattern("d MMM yyyy").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(item.updatedAt))}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (secret.type == "login") {
            if (secret.website.isNotBlank()) VaultValue("Website", secret.website) { copySensitive(context, secret.website) }
            if (secret.username.isNotBlank()) VaultValue("Username", secret.username) { copySensitive(context, secret.username) }
            if (secret.password.isNotBlank()) {
                MomentumTextField(
                    value = secret.password,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        Row {
                            IconButton(onClick = { reveal = !reveal }) { Icon(if (reveal) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "Show or hide password") }
                            IconButton(onClick = { copySensitive(context, secret.password) }) { Icon(Icons.Rounded.ContentCopy, "Copy password") }
                        }
                    }
                )
            }
            if (secret.totp.isNotBlank()) VaultValue("TOTP secret", secret.totp) { copySensitive(context, secret.totp) }
        }
        if (secret.folder.isNotBlank()) Text("Folder: ${secret.folder}", style = MaterialTheme.typography.bodySmall)
        if (secret.tags.isNotBlank()) Text("Tags: ${secret.tags}", style = MaterialTheme.typography.bodySmall)
        if (secret.notes.isNotBlank()) {
            Text(if (secret.type == "note") "Note" else "Notes", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            Text(secret.notes)
            TextButton(onClick = { copySensitive(context, secret.notes) }) { Icon(Icons.Rounded.ContentCopy, null); Spacer(Modifier.width(6.dp)); Text("Copy note") }
        }
        BigButton("Edit item", onClick = onEdit)
        TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.DeleteOutline, null, tint = MaterialTheme.colorScheme.error); Spacer(Modifier.width(6.dp)); Text("Delete item", color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun VaultValue(label: String, value: String, onCopy: () -> Unit) {
    MomentumTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        singleLine = true,
        trailingIcon = { IconButton(onClick = onCopy) { Icon(Icons.Rounded.ContentCopy, "Copy $label") } }
    )
}

@Composable
private fun VaultEntryEditor(initial: VaultListItem?, onDismiss: () -> Unit, onSave: (VaultSecret) -> Unit) {
    var type by remember(initial?.id) { mutableStateOf(initial?.secret?.type ?: "login") }
    var title by remember(initial?.id) { mutableStateOf(initial?.secret?.title ?: "") }
    var website by remember(initial?.id) { mutableStateOf(initial?.secret?.website ?: "") }
    var username by remember(initial?.id) { mutableStateOf(initial?.secret?.username ?: "") }
    var password by remember(initial?.id) { mutableStateOf(initial?.secret?.password ?: "") }
    var notes by remember(initial?.id) { mutableStateOf(initial?.secret?.notes ?: "") }
    var folder by remember(initial?.id) { mutableStateOf(initial?.secret?.folder ?: "") }
    var favorite by remember(initial?.id) { mutableStateOf(initial?.secret?.favorite ?: false) }
    var category by remember(initial?.id) {
        mutableStateOf(initial?.secret?.let(::inferredVaultCategory) ?: "Other")
    }
    var showPassword by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    FormSheet(if (initial == null) "Add secure item" else "Edit secure item", onDismiss) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MomentumChip(selected = type == "login", onClick = { type = "login" }, label = "Login", icon = Icons.Rounded.Key)
            MomentumChip(selected = type == "note", onClick = { type = "note" }, label = "Secure note", icon = Icons.AutoMirrored.Rounded.StickyNote2)
        }
        MomentumTextField(
            value = title,
            onValueChange = { title = it; error = null },
            label = { Text("Title") },
            placeholder = { Text(if (type == "login") "e.g. Email, Bank" else "e.g. Recovery codes") },
            singleLine = true
        )
        MomentumTextField(
            value = folder,
            onValueChange = { folder = it.trimStart('/') },
            label = { Text("Folder / subfolder (optional)") },
            placeholder = { Text("Personal/Accounts") },
            singleLine = true
        )
        if (type == "login") {
            Text("Section", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                vaultCategories.drop(1).forEach { label ->
                    MomentumChip(selected = category == label, onClick = { category = label }, label = label)
                }
            }
            MomentumTextField(
                value = website,
                onValueChange = { website = it },
                label = { Text("Website (optional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )
            MomentumTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username or email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )
            MomentumTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "Show or hide password")
                    }
                }
            )
            MomentumTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes (optional)") }, minLines = 2, maxLines = 5)
        } else {
            MomentumTextField(value = notes, onValueChange = { notes = it }, label = { Text("Secure note") }, minLines = 5, maxLines = 12)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = favorite, onCheckedChange = { favorite = it })
            Text("Favorite")
        }
        if (error != null) Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        BigButton(if (initial == null) "Save" else "Save changes", onClick = {
            if (title.isBlank()) error = "Add a title to save this item."
            else if (type == "login" && password.isBlank()) error = "Add a password."
            else onSave(
                if (type == "login") VaultSecret(
                    title.trim(), type, username.trim(), password, website.trim(), notes.trim(),
                    folder.trim('/'), initial?.secret?.tags ?: "", favorite,
                    initial?.secret?.totp ?: "", category
                )
                else VaultSecret(
                    title.trim(), type, "", "", "", notes.trim(),
                    folder.trim('/'), initial?.secret?.tags ?: "", favorite,
                    category = "Other"
                )
            )
        })
    }
}

private fun copySensitive(context: Context, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Sensitive vault value", value)
    if (Build.VERSION.SDK_INT >= 33) {
        val extras = clip.description.extras ?: PersistableBundle().also { clip.description.extras = it }
        extras.putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
    }
    clipboard.setPrimaryClip(clip)
    val hash = java.security.MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
    val work = OneTimeWorkRequestBuilder<SensitiveClipboardWorker>()
        .setInitialDelay(30, TimeUnit.SECONDS)
        .setInputData(workDataOf(SensitiveClipboardWorker.KEY_HASH to VaultCrypto.encode(hash)))
        .build()
    WorkManager.getInstance(context).enqueue(work)
    Toast.makeText(context, "Copied. Clipboard clears after 30 seconds if unchanged.", Toast.LENGTH_SHORT).show()
    Handler(Looper.getMainLooper()).postDelayed({
        val current = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
        if (current == value) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                clipboard.clearPrimaryClip()
            } else {
                clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        }
    }, 30_000)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
