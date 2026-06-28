package work.lclpnet.ap2.mode_default.util

import work.lclpnet.activity.Activity

fun interface ActivitySwitcher {
    fun switchTo(activity: Activity)
}
