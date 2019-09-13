package com.mattskala.trezorwallet.ui

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.appcompat.app.AlertDialog
import android.view.inputmethod.InputMethodManager
import com.mattskala.trezorwallet.R
import kotlinx.android.synthetic.main.dialog_edit_text.view.*

/**
 * A dialog fragment containing EditText.
 */
class LabelDialogFragment : androidx.fragment.app.DialogFragment() {
    companion object {
        const val ARG_TITLE = "title"
        const val ARG_TEXT = "text"
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = activity!!.layoutInflater.inflate(R.layout.dialog_edit_text, null, false)

        if (savedInstanceState == null) {
            view.editText.setText(arguments?.getString(ARG_TEXT))
        }

        val dialog = AlertDialog.Builder(context!!)
                .setTitle(arguments?.getString(ARG_TITLE))
                .setView(view)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val activity = this.activity
                    if (activity is EditTextDialogListener) {
                        val text = view.editText.text.toString()
                        activity.onTextChanged(text)
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .create()

        dialog.setOnShowListener {
            view.editText.setSelection(view.editText.text.length)

            val imm = context!!.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(view.editText, InputMethodManager.SHOW_IMPLICIT)
        }

        return dialog
    }

    interface EditTextDialogListener {
        fun onTextChanged(text: String)
    }
}