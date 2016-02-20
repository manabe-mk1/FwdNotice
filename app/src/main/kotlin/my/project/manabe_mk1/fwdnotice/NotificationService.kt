package my.project.manabe_mk1.fwdnotice

import android.app.Service
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

import java.util.*
import java.text.SimpleDateFormat

val ACTION_NOTIFICATION_POSTED  = "my.project.manabe_mk1.fwdnotice.posted"
val ACTION_NOTIFICATION_FORWARD = "my.project.manabe_mk1.fwdnotice.forward"

/**
 *
 * 端末側で通知へのアクセス許可を与える必要がある
 * Android 4.* [設定][セキュリティ][通知へのアクセス]
 * Android 5.* [設定][音と通知][通知へのアクセス]
 *
 */
class NotificationService : NotificationListenerService() {

    private fun formatNotice(notice: StatusBarNotification): String {
        return "[%s]%s%s %s(%d) (%s)".format(
                SimpleDateFormat("yy/MM/dd hh:mm:ss").format(Date(notice.postTime)),
                if(notice.isClearable) "[Clear]"   else "",
                if(notice.isOngoing)   "[OnGoing]" else "",
                notice.notification.tickerText,
                notice.id,
                notice.packageName
        )
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.d("Service", "start")
        return Service.START_STICKY;
    }

    override fun onDestroy() {
        Log.d("Service", "stop")
    }

    override fun onNotificationPosted(notice: StatusBarNotification, rank: NotificationListenerService.RankingMap) {
        val (idSet, isForward) = NoticeDb.getDb(applicationContext).insertNotice(notice)
        val noticeId = idSet.first
        val newPackageId = idSet.second
        val isDisplay = (noticeId > 0)

        Log.d("Posted", "%s%s".format(if(isDisplay) "[$noticeId]" else "[Excluded] " , formatNotice(notice)))

        if(isDisplay) {
            var intentP = Intent(ACTION_NOTIFICATION_POSTED)
            intentP.putExtra("noticeId", noticeId)
            intentP.putExtra("newPackageId", newPackageId)
            sendBroadcast(intentP)

            if(isForward) {
                var intentF = Intent(ACTION_NOTIFICATION_FORWARD)
                intentF.putExtra("noticeId", noticeId)
                Mail.getSakuraMail(this).sendAsync(
                        getString(R.string.notice_dest),
                        "${notice.packageName} [FwdNotice]",
                        "${notice.notification.tickerText}",
                        intentF)
            } else {
                Log.d("Forward", "not target notice")
            }
        }
    }

    override fun onNotificationRemoved (notice: StatusBarNotification, rank: NotificationListenerService.RankingMap) {
        Log.d("Removed", formatNotice(notice))
    }
}