package net.taler.merchantpos.debug

import android.content.Intent
import net.taler.merchantpos.MainViewModel
import net.taler.merchantpos.PosDestination

object ScreenshotController {
    val isActive: Boolean
        get() = false

    fun prepareScenario(intent: Intent, model: MainViewModel): PosDestination? = null
}
