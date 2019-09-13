package com.mattskala.trezorwallet.ui.transactiondetail

import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import com.mattskala.trezorwallet.R
import com.mattskala.trezorwallet.data.entity.Transaction
import com.mattskala.trezorwallet.data.entity.TransactionInput
import com.mattskala.trezorwallet.data.entity.TransactionOutput
import com.mattskala.trezorwallet.data.entity.TransactionWithInOut
import com.mattskala.trezorwallet.labeling.LabelingManager
import com.mattskala.trezorwallet.ui.BaseActivity
import com.mattskala.trezorwallet.ui.LabelDialogFragment
import com.mattskala.trezorwallet.ui.formatBtcValue
import kotlinx.android.synthetic.main.activity_transaction_detail.*
import kotlinx.android.synthetic.main.item_transaction_input.view.*
import org.kodein.di.Kodein
import org.kodein.di.generic.bind
import org.kodein.di.generic.instance
import org.kodein.di.generic.provider
import java.text.SimpleDateFormat
import java.util.*


/**
 * A transaction detail activity.
 */
class TransactionDetailActivity : BaseActivity(), LabelDialogFragment.EditTextDialogListener {
    companion object {
        const val EXTRA_ACCOUNT_ID = "account_id"
        const val EXTRA_TXID = "txid"
    }

    private val labeling: LabelingManager by instance()
    private val viewModel: TransactionDetailViewModel by instance()

    override fun provideOverridingModule() = Kodein.Module("TransactionDetail") {
        bind<TransactionDetailViewModel>() with provider {
            ViewModelProviders.of(this@TransactionDetailActivity)[TransactionDetailViewModel::class.java]
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_transaction_detail)

        val txid = intent.getStringExtra(EXTRA_TXID)
        txtHash.text = txid

        viewModel.start(intent.getStringExtra(EXTRA_ACCOUNT_ID), intent.getStringExtra(EXTRA_TXID))

        viewModel.transaction.observe(this, Observer {
            if (it != null) {
                showTransaction(it)
            }
        })
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.transaction_detail, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.web -> {
                showTransactionOnWeb()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onTextChanged(text: String) {
        viewModel.setOutputLabel(text)
    }

    private fun showTransaction(transaction: TransactionWithInOut) {
        val blocktime = transaction.tx.blocktime
        if (blocktime != null && blocktime > 0) {
            txtConfirmed.setText(R.string.confirmed)
            txtBlockTime.visibility = View.VISIBLE
            txtBlockHeight.visibility = View.VISIBLE
            val date = Date(blocktime * 1000)
            txtBlockTime.text = SimpleDateFormat.getDateTimeInstance(SimpleDateFormat.LONG,
                    SimpleDateFormat.MEDIUM).format(date)
            txtBlockHeight.text = getString(R.string.in_block_x, transaction.tx.blockheight.toString())
        } else {
            txtConfirmed.setText(R.string.tx_unconfirmed)
            txtBlockTime.visibility = View.GONE
            txtBlockHeight.visibility = View.GONE
        }

        inputs.removeAllViews()
        transaction.vin.forEach {
            addInputView(it)
        }

        outputs.removeAllViews()
        transaction.vout.forEach {
            addOutputView(transaction, it)
        }

        txtFee.text = formatBtcValue(transaction.tx.fee)
        val feePerByte = transaction.tx.fee / transaction.tx.size
        txtFeePerByte.text = getString(R.string.sat_per_byte, feePerByte.toString())
    }

    private fun addInputView(input: TransactionInput) {
        val view = TransactionInOutView(this)
        view.setValue(input.value)
        view.setAddress(input.addr)
        view.setLabelEnabled(false)
        view.setOnClickListener {
            if (input.addr != null) {
                showAddressOnWeb(input.addr)
            }
        }
        inputs.addView(view)
    }

    private fun addOutputView(transaction: TransactionWithInOut, output: TransactionOutput) {
        val view = TransactionInOutView(this)
        view.setValue(output.value)
        view.setAddress(output.getDisplayLabel(resources))
        val labelable = if (transaction.tx.type == Transaction.Type.RECV)
            output.isMine else
            !output.isChange
        view.setLabelEnabled(labeling.isEnabled() && labelable)
        view.setOnClickListener {
            output.addr?.let {
                showAddressOnWeb(it)
            }
        }
        view.btnLabel.setOnClickListener {
            showLabelDialog(output)
        }
        outputs.addView(view)
    }

    private fun showTransactionOnWeb() {
        val txid = viewModel.transaction.value!!.tx.txid
        val url = "https://blockchain.info/tx/$txid"
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(browserIntent)
    }

    private fun showAddressOnWeb(address: String) {
        val url = "https://blockchain.info/address/$address"
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(browserIntent)
    }

    private fun showLabelDialog(output: TransactionOutput) {
        viewModel.selectedOutput = output
        val fragment = LabelDialogFragment()
        val title = resources.getString(R.string.output_label)
        val label = output.label ?: ""
        val args = Bundle()
        args.putString(LabelDialogFragment.ARG_TITLE, title)
        args.putString(LabelDialogFragment.ARG_TEXT, label)
        fragment.show(supportFragmentManager, "dialog")
    }
}