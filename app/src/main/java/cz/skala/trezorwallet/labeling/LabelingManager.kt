package cz.skala.trezorwallet.labeling

import android.content.Context
import com.google.protobuf.ByteString
import com.satoshilabs.trezor.intents.hexToBytes
import com.satoshilabs.trezor.intents.toHex
import com.satoshilabs.trezor.intents.ui.data.CipherKeyValueRequest
import com.satoshilabs.trezor.lib.protobuf.TrezorMessage
import cz.skala.trezorwallet.crypto.*
import cz.skala.trezorwallet.data.AppDatabase
import cz.skala.trezorwallet.data.PreferenceHelper
import cz.skala.trezorwallet.data.entity.Account
import cz.skala.trezorwallet.data.entity.Address
import cz.skala.trezorwallet.data.entity.TransactionOutput
import org.jetbrains.anko.coroutines.experimental.bg
import org.json.JSONObject
import java.io.File
import java.security.InvalidKeyException


class LabelingManager(
        private val context: Context,
        private val prefs: PreferenceHelper,
        private val database: AppDatabase
) {
    companion object {
        private const val CIPHER_KEY = "Enable labeling?"
        private const val CIPHER_VALUE = "fedcba98765432100123456789abcdeffedcba98765432100123456789abcdef"
        private const val CONSTANT = "0123456789abcdeffedcba9876543210"
        private const val METADATA_EXTENSION = ".mtdt"

        /**
         * Returns a TREZOR request for deriving the master key.
         */
        fun createCipherKeyValueRequest(): CipherKeyValueRequest {
            val cipherKeyValue = TrezorMessage.CipherKeyValue.newBuilder()
                    .addAddressN(hardened(10015))
                    .addAddressN(hardened(0))
                    .setKey(CIPHER_KEY)
                    .setValue(ByteString.copyFrom(CIPHER_VALUE.hexToBytes()))
                    .setEncrypt(true)
                    .setAskOnEncrypt(true)
                    .setAskOnDecrypt(true)
                    .build()
            return CipherKeyValueRequest(cipherKeyValue)
        }

        /**
         * Derives the account key from the master key and xpub.
         */
        fun deriveAccountKey(masterKey: ByteArray, xpub: String): String {
            val accountKey = hmacSha256(masterKey, xpub.toByteArray())
            return encodeBase58Check(accountKey)
        }

        /**
         * Derives the filename and password from the account key.
         */
        fun deriveFilenameAndPassword(accountKey: String): Pair<String, ByteArray> {
            val result = hmacSha512(accountKey.toByteArray(), CONSTANT.hexToBytes())
            val left = result.copyOfRange(0, 32)
            val filename = left.toHex() + METADATA_EXTENSION
            val password = result.copyOfRange(32, 64)
            return Pair(filename, password)
        }

        /**
         * Encrypts the file content with a password.
         */
        fun encryptFile(content: String, password: ByteArray): ByteArray {
            val bytes = content.toByteArray()
            val (iv, tag, cipherText) = encryptAesGcm(bytes, password)
            return iv + tag + cipherText
        }

        /**
         * Decrypts the file content with a password.
         */
        fun decryptFile(bytes: ByteArray, password: ByteArray): String {
            val iv = bytes.copyOfRange(0, 12)
            val tag = bytes.copyOfRange(12, 28)
            val cipherText = bytes.copyOfRange(28, bytes.size)
            val plainText = decryptAesGcm(iv, tag, cipherText, password)
            return plainText.toString(Charsets.UTF_8)
        }
    }

    /**
     * Returns whetter the labeling is enabled.
     */
    fun isEnabled(): Boolean {
        return getMasterKey() != null
    }

    /**
     * Enables labeling by setting the master key.
     */
    suspend fun enableLabeling(masterKey: ByteArray) {
        bg {
            setMasterKey(masterKey)
            fetchAccountsMetadata()
        }.await()
    }

    /**
     * Deletes all metadata files, clears labels in the database and removes the master key.
     */
    suspend fun disableLabeling() {
        bg {
            removeMetadataFiles()
            clearDatabaseLabels()
            setMasterKey(null)
        }.await()
    }

    /**
     * Updates an account label.
     */
    suspend fun setAccountLabel(account: Account, label: String) {
        val resources = context.resources
        val defaultLabel = account.getDefaultLabel(resources)
        val savedLabel = if (label != defaultLabel) label else null
        account.label = savedLabel

        bg {
            database.accountDao().insert(account)

            val metadata = loadMetadata(account)
            if (metadata != null) {
                metadata.accountLabel = label
                saveMetadata(account, metadata)
            }
        }.await()
    }

    /**
     * Updates an address label.
     */
    suspend fun setAddressLabel(address: Address, label: String) {
        address.label = label

        bg {
            database.addressDao().insert(address)

            val account = database.accountDao().getById(address.account)
            val metadata = loadMetadata(account)
            if (metadata != null) {
                metadata.setAddressLabel(address.address, label)
                saveMetadata(account, metadata)
            }
        }.await()
    }

    /**
     * Updates a transaction output label.
     */
    suspend fun setOutputLabel(output: TransactionOutput, label: String) {
        output.label = label

        bg {
            database.transactionDao().insert(output)

            val account = database.accountDao().getById(output.account)
            val metadata = loadMetadata(account)
            if (metadata != null) {
                metadata.setOutputLabel(output.txid, output.n, label)
                saveMetadata(account, metadata)
            }
        }.await()
    }

    /**
     * Loads metadata from file for the specified account.
     */
    fun loadMetadata(account: Account): AccountMetadata? {
        val masterKey = getMasterKey() ?: throw InvalidKeyException("Master key is null")
        val accountKey = deriveAccountKey(masterKey, account.xpub)
        val (filename, password) = deriveFilenameAndPassword(accountKey)
        return loadMetadataFromFile(filename, password) ?: AccountMetadata()
    }

    /**
     * Stores the master key.
     */
    private fun setMasterKey(masterKey: ByteArray?) {
        prefs.labelingMasterKey = masterKey
    }

    /**
     * Loads the previously stored master key.
     */
    private fun getMasterKey(): ByteArray? {
        return prefs.labelingMasterKey
    }

    /**
     * Save metadata to file.
     */
    private fun saveMetadata(account: Account, metadata: AccountMetadata) {
        val masterKey = getMasterKey() ?: throw InvalidKeyException("Master key is null")
        val accountKey = deriveAccountKey(masterKey, account.xpub)
        val (filename, password) = deriveFilenameAndPassword(accountKey)
        saveMetadataToFile(metadata, filename, password)
    }

    /**
     * Decrypts the file with provided password and deserializes the metadata structure.
     */
    private fun loadMetadataFromFile(filename: String, password: ByteArray): AccountMetadata? {
        val file = File(context.filesDir, filename)
        if (!file.exists()) return null
        val bytes = file.readBytes()
        val content = decryptFile(bytes, password)
        val json = JSONObject(content)
        return AccountMetadata.fromJson(json)
    }

    /**
     * Serializes the structure into JSON, encrypts it with [password] and saves it to [filename].
     */
    private fun saveMetadataToFile(metadata: AccountMetadata, filename: String, password: ByteArray) {
        val file = File(context.filesDir, filename)
        val content = metadata.toJson().toString()
        val data = encryptFile(content, password)
        file.writeBytes(data)
    }

    /**
     * Removes metadata files for all accounts. Should be called before disabling labeling or
     * forgetting the device.
     */
    private fun removeMetadataFiles() {
        val masterKey = getMasterKey() ?: throw InvalidKeyException("Master key is null")
        val accounts = database.accountDao().getAll()
        accounts.forEach {
            val accountKey = deriveAccountKey(masterKey, it.xpub)
            val (filename, _) = deriveFilenameAndPassword(accountKey)
            val file = File(context.filesDir, filename)
            file.delete()
        }
    }

    /**
     * Removes all labels from the database.
     */
    private fun clearDatabaseLabels() {
        database.accountDao().clearLabels()
        database.addressDao().clearLabels()
        database.transactionDao().clearLabels()
    }

    /**
     * Fetches metadata for all accounts from Dropbox and updates labels in the database.
     */
    private fun fetchAccountsMetadata() {
        val accounts = database.accountDao().getAll()
        accounts.forEach {
            fetchAccountMetadata(it)
        }
    }

    /**
     * Fetches account metadata from Dropbox and updates labels in the database.
     */
    private fun fetchAccountMetadata(account: Account) {
        // TODO
    }

    /**
     * Uploads account metadata file to Dropbox.
     */
    private fun uploadAccountMetadata(account: Account) {
        // TODO
    }
}