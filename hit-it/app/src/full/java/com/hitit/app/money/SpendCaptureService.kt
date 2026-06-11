package com.hitit.app.money

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.hitit.app.data.repository.MoneyRepository
import com.hitit.domain.money.NotificationSpendParser
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The auto-integration listener: when Zomato/Swiggy/Uber/GPay/… post an order or payment
 * notification, the domain parser turns it into a categorized spend in MoneyVibe — no typing.
 *
 * Privacy by construction: the user must explicitly grant Notification Access in system settings
 * (the OS gates this service until then); only packages the parser knows are even looked at; text
 * is parsed in-memory and only (amount, merchant, category) is stored; the app has no INTERNET, so
 * nothing read here can ever leave the device.
 */
@AndroidEntryPoint
class SpendCaptureService : NotificationListenerService() {

    @Inject
    lateinit var moneyRepository: MoneyRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // Group summaries and ongoing notifications (rides in progress etc.) repeat — skip them.
        if (sbn.isOngoing || (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        if (sbn.packageName !in NotificationSpendParser.KNOWN_APPS) return

        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val big = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()

        val captured = NotificationSpendParser.parse(sbn.packageName, title, if (big.length > text.length) big else text)
            ?: return
        scope.launch { runCatching { moneyRepository.logCaptured(captured) } }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
