package com.mattskala.trezorwallet.data.entity

import android.annotation.SuppressLint
import androidx.room.Entity
import android.os.Parcelable
import com.mattskala.trezorwallet.BuildConfig
import com.mattskala.trezorwallet.crypto.ExtendedPublicKey
import kotlinx.android.parcel.Parcelize

/**
 * An address entity.
 */
@SuppressLint("ParcelCreator")
@Entity(tableName = "addresses", primaryKeys = ["address", "account"])
@Parcelize
class Address(
        val address: String,
        val account: String,
        val change: Boolean,
        val index: Int,
        var label: String?,
        var totalReceived: Long
) : Parcelable {
    fun getPath(account: Account): IntArray {
        val path = IntArray(5)
        path[0] = (ExtendedPublicKey.HARDENED_IDX + if (account.legacy) 44 else 49).toInt()
        path[1] = (ExtendedPublicKey.HARDENED_IDX + BuildConfig.COIN_TYPE).toInt()
        path[2] = (ExtendedPublicKey.HARDENED_IDX + account.index).toInt()
        path[3] = if (change) 1 else 0
        path[4] = index
        return path
    }

    fun getPathString(account: Account): String {
        var path = "m"
        path += if (account.legacy) "/44'" else "/49'"
        path += "/" + BuildConfig.COIN_TYPE + "'"
        path += "/" + account.index + "'"
        path += if (change) "/1" else "/0"
        path += "/$index"
        return path
    }
}