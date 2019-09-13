package com.mattskala.trezorwallet.ui.main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.mattskala.trezorwallet.R
import com.mattskala.trezorwallet.data.entity.Account
import com.mattskala.trezorwallet.data.item.AccountItem
import com.mattskala.trezorwallet.data.item.AccountSectionItem
import com.mattskala.trezorwallet.data.item.AddAccountItem
import com.mattskala.trezorwallet.data.item.Item
import com.mattskala.trezorwallet.ui.formatBtcValue
import kotlinx.android.synthetic.main.item_account.view.*
import kotlinx.android.synthetic.main.item_account_add.view.*

/**
 * Accounts list adapter.
 */
class AccountsAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    companion object {
        private const val TYPE_ACCOUNT = 1
        private const val TYPE_SECTION = 2
        private const val TYPE_BUTTON = 3
    }

    var items: List<Item> = mutableListOf()
    var selectedAccount: Account? = null
    var onItemClickListener: ((account: Account) -> Unit)? = null
    var onAddAccountListener: ((legacy: Boolean) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_ACCOUNT -> {
                val view = inflater.inflate(R.layout.item_account, parent, false)
                AccountViewHolder(view)
            }
            TYPE_SECTION -> {
                val view = inflater.inflate(R.layout.item_account_section, parent, false)
                SectionViewHolder(view)
            }
            TYPE_BUTTON -> {
                val view = inflater.inflate(R.layout.item_account_add, parent, false)
                ButtonViewHolder(view)
            }
            else -> throw IllegalArgumentException("Invalid viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (getItemViewType(position)) {
            TYPE_ACCOUNT -> {
                holder as AccountViewHolder
                val item = items[position] as AccountItem
                holder.bind(item.account, selectedAccount == item.account)
                holder.itemView.setOnClickListener {
                    selectedAccount = holder.account
                    notifyDataSetChanged()
                    onItemClickListener?.invoke(item.account)
                }
            }
            TYPE_BUTTON -> {
                holder as ButtonViewHolder
                val item = items[position] as AddAccountItem
                holder.bind(item.legacy)
                holder.itemView.setOnClickListener {
                    onAddAccountListener?.invoke(item.legacy)
                }
            }
        }
    }

    override fun getItemCount(): Int {
        return items.size
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is AccountItem -> TYPE_ACCOUNT
            is AccountSectionItem -> TYPE_SECTION
            is AddAccountItem -> TYPE_BUTTON
            else -> -1
        }
    }

    class AccountViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var account: Account? = null

        fun bind(acc: Account, selected: Boolean) = with(itemView) {
            account = acc
            txtAccountLabel.text = acc.getDisplayLabel(resources)
            txtAccountBalance.text = formatBtcValue(acc.balance, 2)
            isSelected = selected
        }
    }

    class SectionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    class ButtonViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(legacy: Boolean) = with(itemView) {
            txtButtonLabel.setText(if (legacy) R.string.add_legacy_account else R.string.add_account)
        }
    }
}