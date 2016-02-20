package my.project.manabe_mk1.fwdnotice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.support.v4.app.ListFragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CompoundButton
import android.widget.Switch
import android.widget.TextView

class PackageListFragment: ListFragment()
{
    private val recieverPosted: NotificationPostedReceiver by lazy {
        NotificationPostedReceiver(adapter)
    }

    private val adapter: ArrayAdapter<NoticeDb.Package> by lazy {
        object : ArrayAdapter<NoticeDb.Package>(activity, R.layout.list_item_package, R.id.text_package_name) {

            inner class ViewCache(name: TextView, forward: Switch, ignore: Switch) {
                val name = name
                val forward = forward
                val ignore = ignore
            }

            abstract inner class SwitchListener(pkg: NoticeDb.Package): CompoundButton.OnCheckedChangeListener {
                val pkg = pkg
            }

            override fun getView(position: Int, view: View?, parent: ViewGroup?): View {
                val rPosition = count - (position +1) // ListView に新着順で表示させる
                val pkg = getItem(rPosition)

                Log.v("getView Package", "%s: %b, %b".format(pkg.packageName, pkg.isForward, pkg.isIgnore))

                val copy  = super.getView(rPosition, view, parent)
                if(copy.tag == null) {
                    val tag = ViewCache(
                        copy.findViewById(R.id.text_package_name) as TextView,
                        copy.findViewById(R.id.switch_forward) as Switch,
                        copy.findViewById(R.id.switch_ignore) as Switch
                    )

                    tag.forward.setOnCheckedChangeListener(
                        object: SwitchListener(pkg) {
                            override fun onCheckedChanged(p0: CompoundButton?, isChecked: Boolean) {
                                Log.d("SwitchFoward", "%s(%d): %b".format(pkg.packageName, pkg.packageId, isChecked))
                                NoticeDb.getDb(activity).updatePackageForward(pkg.packageId, isChecked)
                            }
                        })


                    tag.ignore.setOnCheckedChangeListener(
                        object: SwitchListener(pkg) {
                            override fun onCheckedChanged(p0: CompoundButton?, isChecked: Boolean) {
                                Log.d("SwitchIgnore", "%s(%d): %b".format(pkg.packageName, pkg.packageId, isChecked))
                                NoticeDb.getDb(activity).updatePackageIgnore(pkg.packageId, isChecked)
                            }
                        })

                    tag.name.text = pkg.toString()
                    tag.forward.isChecked = pkg.isForward
                    tag.ignore.isChecked = pkg.isIgnore
                    copy.tag = tag
                }

                return copy
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        listAdapter = adapter

        NoticeDb.getDb(activity).selectPackageList({
            var pack = NoticeDb.Package(it)
            Log.v("Add Package", "%s: %b, %b".format(pack.packageName, pack.isForward, pack.isIgnore))
            adapter.add(pack)
        })

        activity.registerReceiver(recieverPosted, NotificationPostedReceiver.getIntentFilter())
    }

    override fun onDestroy() {
        activity.unregisterReceiver(recieverPosted)
        super.onDestroy()
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater?.inflate(R.layout.fragment_main, container, false)
    }

    class NotificationPostedReceiver(adapter: ArrayAdapter<NoticeDb.Package>) : BroadcastReceiver()
    {
        private var list = adapter

        companion object {
            fun getIntentFilter(): IntentFilter {
                var filter = IntentFilter()
                filter.addAction(ACTION_NOTIFICATION_POSTED)
                return filter
            }
        }

        override fun onReceive(context: Context, intent: Intent) {
            var packageId = intent.getLongExtra("newPackageId", 0)
            Log.v("Action Posted (Package)", "$packageId")
            if(packageId > 0) {
                NoticeDb.getDb(context).selectPackageById(packageId, {
                    list.add(NoticeDb.Package(it))
                })
            }
        }
    }
}