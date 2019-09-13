package com.mattskala.trezorwallet.data.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mattskala.trezorwallet.data.entity.Transaction
import com.mattskala.trezorwallet.data.entity.TransactionInput
import com.mattskala.trezorwallet.data.entity.TransactionOutput
import com.mattskala.trezorwallet.data.entity.TransactionWithInOut

/**
 * A transaction DAO.
 */
@Dao
abstract class TransactionDao {
    @Query("SELECT * FROM transactions WHERE txid = :txid AND account = :account")
    @androidx.room.Transaction
    abstract fun getByTxid(account: String, txid: String): TransactionWithInOut

    @Query("SELECT * FROM transactions WHERE account = :account")
    abstract fun getByAccount(account: String): List<Transaction>

    @Query("SELECT * FROM transactions WHERE account = :account")
    @androidx.room.Transaction
    abstract fun getByAccountLiveDataWithInOut(account: String): LiveData<List<TransactionWithInOut>>

    @Query("SELECT * FROM transaction_outputs WHERE account = :account AND isMine = 1")
    abstract fun getMyOutputs(account: String): List<TransactionOutput>

    @Query("SELECT * FROM transaction_inputs WHERE account = :account")
    abstract fun getInputs(account: String): List<TransactionInput>

    @Query("UPDATE transaction_outputs SET label = :label WHERE account = :account AND txid = :txid AND n = :index")
    abstract fun updateLabel(account: String, txid: String, index: Int, label: String?)

    @Query("UPDATE transaction_outputs SET label = null")
    abstract fun clearLabels()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insert(transaction: Transaction)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insert(input: TransactionInput)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insert(output: TransactionOutput)

    @androidx.room.Transaction
    open fun insert(transaction: TransactionWithInOut) {
        insert(transaction.tx)
        transaction.vin.forEach {
            insert(it)
        }
        transaction.vout.forEach {
            insert(it)
        }
    }

    @androidx.room.Transaction
    open fun insertTransactions(transactions: List<TransactionWithInOut>) {
        transactions.forEach {
            insert(it)
        }
    }

    @Query("DELETE FROM transactions")
    abstract fun deleteTransactions()

    @Query("DELETE FROM transaction_inputs")
    abstract fun deleteTransactionInputs()

    @Query("DELETE FROM transaction_outputs")
    abstract fun deleteTransactionOutputs()

    @androidx.room.Transaction
    open fun deleteAll() {
        deleteTransactions()
        deleteTransactionInputs()
        deleteTransactionOutputs()
    }
}