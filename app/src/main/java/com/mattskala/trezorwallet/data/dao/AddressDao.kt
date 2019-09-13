package com.mattskala.trezorwallet.data.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mattskala.trezorwallet.data.entity.Address

/**
 * An address DAO.
 */
@Dao
interface AddressDao {
    @Query("SELECT * FROM addresses WHERE account = :account AND address = :address")
    fun getByAddress(account: String, address: String): Address

    @Query("SELECT * FROM addresses WHERE address = :address")
    fun getByAddress(address: String): List<Address>

    @Query("SELECT * FROM addresses WHERE account = :account AND change = :change ORDER BY `index` ASC")
    fun getByAccount(account: String, change: Boolean): List<Address>

    @Query("SELECT * FROM addresses WHERE account = :account AND change = :change ORDER BY `index` ASC")
    fun getByAccountLiveData(account: String, change: Boolean): LiveData<List<Address>>

    @Query("UPDATE addresses SET label = :label WHERE account = :account AND address = :address")
    fun updateLabel(account: String, address: String, label: String?)

    @Query("UPDATE addresses SET label = null")
    fun clearLabels()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(address: Address)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(addresses: List<Address>)

    @Query("DELETE FROM addresses")
    fun deleteAll()
}