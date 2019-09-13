package com.mattskala.trezorwallet.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.kodein.di.KodeinAware
import org.kodein.di.android.closestKodein


abstract class BaseViewModel(app: Application) : AndroidViewModel(app), KodeinAware {
    override val kodein by closestKodein(app)

    private val job = Job()
    protected val viewModelScope = CoroutineScope(Dispatchers.Main + job)

    override fun onCleared() {
        super.onCleared()
        job.cancel()
    }
}