package io.github.poohcjs.fitkey.ime

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import io.github.poohcjs.fitkey.engine.Otp

/**
 * 새 알림에서 인증번호만 꺼내 키보드 위에 띄운다 (문자·카카오 알림톡·은행 앱 등).
 * 알림 글은 저장하지 않고 숫자만 5분 동안 기억한다. 이 앱은 인터넷 권한이 없어 어디로도 보내지 않는다.
 */
class OtpListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val n = sbn ?: return
        if (n.packageName == packageName || n.isOngoing) return
        val nf = n.notification ?: return
        if ((nf.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        val ex = nf.extras ?: return
        val text = listOfNotNull(
            ex.getCharSequence(Notification.EXTRA_TITLE), ex.getCharSequence(Notification.EXTRA_TEXT),
            ex.getCharSequence(Notification.EXTRA_BIG_TEXT), ex.getCharSequence(Notification.EXTRA_SUB_TEXT),
        ).joinToString(" ") + " " + (ex.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString(" ") ?: "")
        val code = Otp.extract(text) ?: return
        val from = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(n.packageName, 0)).toString() }.getOrDefault("")
        OtpStore.put(code, from)
    }
}
