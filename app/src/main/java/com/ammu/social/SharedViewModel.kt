package com.ammu.social

import androidx.lifecycle.ViewModel
import com.ammu.social.models.FacebookPage

class SharedViewModel : ViewModel() {
    var pages: List<FacebookPage>? = null
}
