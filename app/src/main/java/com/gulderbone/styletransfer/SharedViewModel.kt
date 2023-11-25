package com.gulderbone.styletransfer

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SharedViewModel : ViewModel() {

    val bitmap: MutableStateFlow<Bitmap?> = MutableStateFlow(null)
}
