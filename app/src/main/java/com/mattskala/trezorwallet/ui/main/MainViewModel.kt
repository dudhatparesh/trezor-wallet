package com.mattskala.trezorwallet.ui.main

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.satoshilabs.trezor.intents.ui.data.TrezorRequest
import com.satoshilabs.trezor.lib.protobuf.TrezorType
import com.mattskala.trezorwallet.R
import com.mattskala.trezorwallet.blockbook.BlockbookException
import com.mattskala.trezorwallet.blockbook.BlockbookSocketService
import com.mattskala.trezorwallet.data.AppDatabase
import com.mattskala.trezorwallet.data.PreferenceHelper
import com.mattskala.trezorwallet.data.entity.Account
import com.mattskala.trezorwallet.data.repository.AccountRepository
import com.mattskala.trezorwallet.data.repository.AddressRepository
import com.mattskala.trezorwallet.data.repository.TransactionRepository
import com.mattskala.trezorwallet.discovery.AccountDiscoveryManager
import com.mattskala.trezorwallet.labeling.LabelingManager
import com.mattskala.trezorwallet.ui.BaseViewModel
import com.mattskala.trezorwallet.ui.SingleLiveEvent
import kotlinx.coroutines.*
import org.kodein.di.generic.instance

/**
 * A ViewModel for MainActivity.
 */
class MainViewModel(app: Application) : BaseViewModel(app) {
    private val database: AppDatabase by instance()
    private val labeling: LabelingManager by instance()
    private val prefs: PreferenceHelper by instance()
    private val blockbookSocketService: BlockbookSocketService by instance()
    private val transactionRepository: TransactionRepository by instance()
    private val accountRepository: AccountRepository by instance()
    private val addressRepository: AddressRepository by instance()

    val accounts: LiveData<List<Account>> by lazy {
        accountRepository.getAllLiveData()
    }

    val selectedAccount = MutableLiveData<Account>()
    var selectedTab = R.id.item_transactions
    val labelingState = MutableLiveData<LabelingState>()

    val onTrezorRequest = SingleLiveEvent<TrezorRequest>()
    val onLastAccountEmpty = SingleLiveEvent<Nothing>()

    private var isAccountRequestLegacy = false

    private val blockbookConnectionListener = object : BlockbookSocketService.ConnectionListener {
        override fun onConnect() {
            viewModelScope.launch {
                val info = blockbookSocketService.getInfo()
                prefs.blockHeight = info.blocks

                initBlockbookSubscription()
            }
        }

        override fun onDisconnect() {
        }
    }

    private val blockbookSubscriptionListener = object : BlockbookSocketService.SubscriptionListener {
        override fun onHashblock(hash: String) {
            viewModelScope.launch {
                try {
                    // Fetch current block height
                    val info = blockbookSocketService.getInfo()
                    prefs.blockHeight = info.blocks

                    // Refresh transactions
                    val accounts = accountRepository.getAll()
                    accounts.forEach {
                        transactionRepository.refresh(it.id)
                    }
                } catch (e: BlockbookException) {
                    e.printStackTrace()
                }
            }
        }

        override fun onAddressTxid(address: String, txid: String) {
            viewModelScope.launch {
                try {
                    // Fetch the transaction and save it to the database
                    val tx = blockbookSocketService.getDetailedTransaction(txid)
                    val addresses = addressRepository.getByAddress(address)
                    addresses.forEach {
                        transactionRepository.saveTx(tx, it.account)
                    }
                } catch (e: BlockbookException) {
                    e.printStackTrace()
                }
            }
        }
    }

    init {
        labelingState.value = if (labeling.isEnabled())
            LabelingState.ENABLED else LabelingState.DISABLED

        if (labeling.isEnabled()) {
            viewModelScope.launch {
                labeling.downloadAccountsMetadata()
            }
        }

        blockbookSocketService.addConnectionListener(blockbookConnectionListener)
        blockbookSocketService.addSubscriptionListener(blockbookSubscriptionListener)

        if (blockbookSocketService.isConnected()) {
            blockbookConnectionListener.onConnect()
        } else {
            blockbookSocketService.connect()
        }
    }

    enum class LabelingState {
        DISABLED, SYNCING, ENABLED
    }

    override fun onCleared() {
        disconnectBlockbook()
        super.onCleared()
    }

    fun setSelectedAccount(account: Account) {
        if (selectedAccount.value != account) {
            selectedTab = R.id.item_transactions
            selectedAccount.value = account
        }
    }

    fun addAccount(legacy: Boolean) {
        viewModelScope.launch {
            val lastAccount = accountRepository.getAll()
                    .lastOrNull { it.legacy == legacy }

            val lastAccountTransactions = if (lastAccount != null) {
                transactionRepository.getByAccount(lastAccount.id).size
            } else 0

            if (lastAccount == null || lastAccountTransactions > 0) {
                val newIndex = if (lastAccount != null) lastAccount.index + 1 else 0
                isAccountRequestLegacy = legacy
                onTrezorRequest.value =
                        AccountDiscoveryManager.createGetPublicKeyRequest(newIndex, legacy,
                                prefs.deviceState)
            } else {
                onLastAccountEmpty.call()
            }
        }
    }

    fun saveAccount(node: TrezorType.HDNodeType, xpub: String) {
        viewModelScope.launch {
            val account = Account.fromNode(node, xpub, isAccountRequestLegacy)
            accountRepository.insert(account)

            // Update Blockbook subscription
            initBlockbookSubscription()
        }
    }

    fun enableLabeling(masterKey: ByteArray) = viewModelScope.launch {
        labelingState.value = LabelingState.SYNCING
        labeling.enableLabeling(masterKey)
        labelingState.value = LabelingState.ENABLED
    }

    fun disableLabeling() = viewModelScope.launch {
        labeling.disableLabeling()
        labelingState.value = LabelingState.DISABLED
    }

    fun setDropboxToken(token: String) {
        labeling.setDropboxToken(token)
    }

    /**
     * Updates currently selected account label.
     */
    fun setAccountLabel(label: String) = viewModelScope.launch {
        val account = selectedAccount.value!!
        labeling.setAccountLabel(account, label)
    }

    fun forgetDevice() = viewModelScope.launch {
        disconnectBlockbook()

        if (labeling.isEnabled()) {
            labeling.disableLabeling()
        }

        withContext(Dispatchers.IO) {
            database.accountDao().deleteAll()
            database.transactionDao().deleteAll()
            database.addressDao().deleteAll()
            prefs.clear()
        }
    }

    suspend fun initBlockbookSubscription() {
        // Subscribe to new blocks
        blockbookSocketService.subscribeHashblock()

        withContext(Dispatchers.Default) {
            // Subscribe to new transactions on my addresses
            val accounts = database.accountDao().getAll()
            accounts.forEach {  account ->
                val externalChainAddresses = database.addressDao()
                        .getByAccount(account.id, false)
                        .map { it.address }
                val changeAddresses = database.addressDao()
                        .getByAccount(account.id, true)
                        .map { it.address }
                blockbookSocketService.subscribeAddressTxid(externalChainAddresses)
                blockbookSocketService.subscribeAddressTxid(changeAddresses)
            }
        }
    }

    private fun disconnectBlockbook() {
        blockbookSocketService.removeSubscriptionListener(blockbookSubscriptionListener)
        blockbookSocketService.removeConnectionListener(blockbookConnectionListener)
        blockbookSocketService.disconnect()
    }
}