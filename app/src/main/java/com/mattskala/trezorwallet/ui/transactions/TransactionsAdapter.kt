package com.mattskala.trezorwallet.ui.transactions

import android.annotation.SuppressLint
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.RecyclerView
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.mattskala.trezorwallet.R
import com.mattskala.trezorwallet.data.entity.Transaction
import com.mattskala.trezorwallet.data.entity.TransactionWithInOut
import com.mattskala.trezorwallet.data.item.AccountSummaryItem
import com.mattskala.trezorwallet.data.item.DateItem
import com.mattskala.trezorwallet.data.item.Item
import com.mattskala.trezorwallet.data.item.TransactionItem
import com.mattskala.trezorwallet.ui.BTC_TO_SATOSHI
import com.mattskala.trezorwallet.ui.formatBtcValue
import com.mattskala.trezorwallet.ui.formatPrice
import kotlinx.android.synthetic.main.item_account_summary.view.*
import kotlinx.android.synthetic.main.item_transaction.view.*
import kotlinx.android.synthetic.main.item_transaction_date.view.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Transactions list adapter.
 */
class TransactionsAdapter : androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
    companion object {
        private const val TYPE_SUMMARY = 1
        private const val TYPE_DATE = 2
        private const val TYPE_TRANSACTION = 3
    }

    var items: List<Item> = mutableListOf()

    var onTransactionClickListener: ((TransactionWithInOut) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): androidx.recyclerview.widget.RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_SUMMARY -> {
                val view = inflater.inflate(R.layout.item_account_summary, parent, false)
                SummaryViewHolder(view)
            }
            TYPE_DATE -> {
                val view = inflater.inflate(R.layout.item_transaction_date, parent, false)
                DateViewHolder(view)
            }
            TYPE_TRANSACTION -> {
                val view = inflater.inflate(R.layout.item_transaction, parent, false)
                TransactionViewHolder(view)
            }
            else -> throw Exception("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is SummaryViewHolder -> {
                val item = items[position] as AccountSummaryItem
                holder.bind(item.summary, item.rate, item.currencyCode)
            }
            is DateViewHolder -> holder.bind((items[position] as DateItem).date)
            is TransactionViewHolder -> {
                val item = items[position] as TransactionItem
                holder.bind(item.transaction, item.rate, item.currencyCode)
                holder.itemView.setOnClickListener {
                    onTransactionClickListener?.invoke(item.transaction)
                }
            }
        }
    }

    override fun getItemCount(): Int {
        return items.size
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is AccountSummaryItem -> TYPE_SUMMARY
            is DateItem -> TYPE_DATE
            is TransactionItem -> TYPE_TRANSACTION
            else -> 0
        }
    }

    class SummaryViewHolder(itemView: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
        @SuppressLint("SetTextI18n")
        fun bind(summary: AccountSummary, rate: Double, currencyCode: String) = with(itemView) {
            itemBalance.setTitle(R.string.balance)
            itemRate.setTitle(R.string.rate)
            itemReceived.setTitle(R.string.received)
            itemSent.setTitle(R.string.sent)

            val balance = summary.balance
            itemBalance.setValuePrimary(formatBtcValue(balance))
            itemBalance.setValueSecondary(
                    formatPrice((balance.toDouble() / BTC_TO_SATOSHI) * rate, currencyCode))
            itemReceived.setValuePrimary(formatBtcValue(summary.received))
            itemReceived.setValueSecondary(formatPrice(
                    (summary.received.toDouble() / BTC_TO_SATOSHI) * rate, currencyCode))
            itemSent.setValuePrimary(formatBtcValue(summary.sent))
            itemSent.setValueSecondary(
                    formatPrice((summary.sent.toDouble() / BTC_TO_SATOSHI) * rate, currencyCode))
            itemRate.setValuePrimary(formatPrice(rate, currencyCode))
            itemRate.setValueSecondary(formatBtcValue(1.0))
        }
    }

    class DateViewHolder(itemView: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
        fun bind(date: Date?) = with(itemView) {
            txtDate.text = if (date != null)
                SimpleDateFormat.getDateInstance(SimpleDateFormat.LONG).format(date) else
                resources.getString(R.string.tx_unconfirmed)
        }
    }

    class TransactionViewHolder(itemView: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
        @SuppressLint("SetTextI18n")
        fun bind(transaction: TransactionWithInOut, rate: Double, currencyCode: String) = with(itemView) {
            txtDateTime.text = transaction.tx.getBlockTimeFormatted() ?:
                    resources.getString(R.string.tx_unconfirmed)

            val targets = transaction.vout.filter {
                when (transaction.tx.type) {
                    Transaction.Type.SENT -> !it.isMine
                    Transaction.Type.RECV -> it.isMine
                    Transaction.Type.SELF -> !it.isChange
                }
            }

            otherLabels.removeAllViews()

            targets.forEachIndexed { index, output ->
                if (index == 0) {
                    txtLabel.text = output.getDisplayLabel(resources)
                } else {
                    val view = TextView(context)
                    view.text = output.getDisplayLabel(resources)
                    view.ellipsize = TextUtils.TruncateAt.END
                    view.setLines(1)
                    otherLabels.addView(view)
                }
            }

            val sign = if (transaction.tx.type == Transaction.Type.RECV) "+" else "−"
            val value = if (transaction.tx.type == Transaction.Type.RECV) transaction.tx.value
                else transaction.tx.value + transaction.tx.fee
            txtValueBtc.text = sign + formatBtcValue(value)
            val colorRes = when (transaction.tx.type) {
                Transaction.Type.RECV -> R.color.colorPrimary
                else -> R.color.colorRed
            }
            txtValueBtc.setTextColor(ResourcesCompat.getColor(resources, colorRes, null))
            txtValueUsd.text = formatPrice((value.toDouble() / BTC_TO_SATOSHI) * rate, currencyCode)
            txtValueUsd.visibility = View.VISIBLE
        }
    }
}
