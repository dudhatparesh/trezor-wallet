package com.mattskala.trezorwallet.ui.transactions

import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import android.content.Intent
import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.mattskala.trezorwallet.R
import com.mattskala.trezorwallet.data.entity.TransactionWithInOut
import com.mattskala.trezorwallet.ui.BaseFragment
import com.mattskala.trezorwallet.ui.transactiondetail.TransactionDetailActivity
import kotlinx.android.synthetic.main.fragment_transactions.*
import org.kodein.di.Kodein
import org.kodein.di.generic.bind
import org.kodein.di.generic.instance
import org.kodein.di.generic.provider


/**
 * A fragment for transactions list.
 */
class TransactionsFragment : BaseFragment() {
    companion object {
        const val ARG_ACCOUNT_ID = "account_id"
    }

    private val viewModel: TransactionsViewModel by instance()

    private val adapter = TransactionsAdapter()

    override fun provideOverridingModule() = Kodein.Module("Transactions") {
        bind<TransactionsViewModel>() with provider {
            ViewModelProviders.of(this@TransactionsFragment)[TransactionsViewModel::class.java]
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val args = arguments ?: return
        viewModel.start(args.getString(ARG_ACCOUNT_ID)!!)

        viewModel.items.observe(this, Observer {
            if (it != null) {
                adapter.items = it
                adapter.notifyDataSetChanged()
            }
        })

        viewModel.refreshing.observe(this, Observer {
            swipeRefreshLayout.isRefreshing = (it == true)
        })

        viewModel.empty.observe(this, Observer {
            empty.visibility = if (it == true) View.VISIBLE else View.GONE
        })

        viewModel.account.observe(this, Observer {
            if (it != null) {
                btnHideAccount.visibility = if (it.index > 0) View.VISIBLE else View.GONE
            }
        })

        adapter.onTransactionClickListener = {
            startTransactionDetailActivity(it)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_transactions, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
        recyclerView.adapter = adapter

        swipeRefreshLayout.setOnRefreshListener {
            viewModel.fetchTransactions()
        }

        btnHideAccount.setOnClickListener {
            viewModel.removeAccount()
        }
    }

    private fun startTransactionDetailActivity(transaction: TransactionWithInOut) {
        val intent = Intent(activity, TransactionDetailActivity::class.java)
        intent.putExtra(TransactionDetailActivity.EXTRA_ACCOUNT_ID, transaction.tx.account)
        intent.putExtra(TransactionDetailActivity.EXTRA_TXID, transaction.tx.txid)
        startActivity(intent)
    }
}