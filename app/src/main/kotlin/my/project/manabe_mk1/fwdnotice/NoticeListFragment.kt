package my.project.manabe_mk1.fwdnotice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView

class NoticeListFragment : Fragment()
{
    private val recieverPosted: NotificationPostedReceiver by lazy {
        NotificationPostedReceiver(adapter)
    }

    private val adapter: ArrayAdapter<String> by lazy {
        object : ArrayAdapter<String>(activity, android.R.layout.simple_list_item_1) {
            // ListView に新着順で表示させる
            override fun getView(pos: Int, view: View?, parent: ViewGroup?): View {
                return super.getView(count - (pos +1), view, parent)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activity.registerReceiver(recieverPosted, NotificationPostedReceiver.getIntentFilter())
    }

    override fun onDestroy() {
        activity.unregisterReceiver(recieverPosted)
        super.onDestroy()
    }

    override fun onCreateView(inflater: LayoutInflater?,container: ViewGroup?, savedInstanceState: Bundle?): View? {
        inflater ?: return null
        val rootView = inflater.inflate(R.layout.fragment_main, container, false)
        var view = rootView.findViewById(R.id.listView) as ListView

        NoticeDb.getDb(activity).selectNoticeLimit({
            cursor -> adapter.add(NoticeDb.Notice(cursor).toString())
        })

        view.adapter = adapter
        return rootView
    }

    class NotificationPostedReceiver(adapter: ArrayAdapter<String>) : BroadcastReceiver()
    {
        private var listView = adapter

        companion object {
            fun getIntentFilter(): IntentFilter {
                var filter = IntentFilter()
                filter.addAction(ACTION_NOTIFICATION_POSTED)
                filter.addAction(ACTION_NOTIFICATION_FORWARD)
                return filter
            }
        }

        override fun onReceive(context: Context, intent: Intent) {
            Log.v("Intent ${intent.action}", "${intent.getLongExtra("noticeId", 0)}")

            when(intent.action) {
                ACTION_NOTIFICATION_POSTED -> {
                    NoticeDb.getDb(context).selectNoticeById(intent.getLongExtra("noticeId", 0), {
                        c, s -> listView.add(NoticeDb.Notice(c).toString())
                    })
                }

                ACTION_NOTIFICATION_FORWARD -> {

                }
            }
        }
    }
}
