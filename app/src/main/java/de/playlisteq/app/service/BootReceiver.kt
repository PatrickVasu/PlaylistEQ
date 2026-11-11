package de.playlisteq.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.playlisteq.app.core.Store

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && Store.settings.value.enabled) {
            EqService.start(context)
        }
    }
}
