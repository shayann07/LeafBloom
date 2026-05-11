package com.devsphere.leafbloom.util

import android.content.Context
import com.devsphere.leafbloom.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Utility for formatting scan timestamps into user-friendly date/time strings.
 */
object DateUtils {

    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("dd MMM", Locale.getDefault())
    private val fullDateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    /**
     * Returns a section header label: "Today", "Yesterday", or "12 May 2026".
     */
    fun getSectionLabel(context: Context, timestampMs: Long): String {
        val scanCal = Calendar.getInstance().apply { timeInMillis = timestampMs }
        val todayCal = Calendar.getInstance()

        return when {
            isSameDay(scanCal, todayCal) -> context.getString(R.string.today)
            isYesterday(scanCal, todayCal) -> context.getString(R.string.yesterday)
            else -> fullDateFormat.format(Date(timestampMs))
        }
    }

    /**
     * For the history screen items: time only (e.g. "01:44 AM").
     */
    fun getTimeOnly(timestampMs: Long): String = timeFormat.format(Date(timestampMs))

    /**
     * For the home screen: show time if today, otherwise show short date.
     * e.g. "01:44 AM" or "12 May"
     */
    fun getSmartDate(timestampMs: Long): String {
        val scanCal = Calendar.getInstance().apply { timeInMillis = timestampMs }
        val todayCal = Calendar.getInstance()

        return if (isSameDay(scanCal, todayCal)) {
            timeFormat.format(Date(timestampMs))
        } else {
            dateFormat.format(Date(timestampMs))
        }
    }

    private fun isSameDay(a: Calendar, b: Calendar): Boolean =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
                a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    private fun isYesterday(scan: Calendar, today: Calendar): Boolean {
        val yesterday = Calendar.getInstance().apply {
            timeInMillis = today.timeInMillis
            add(Calendar.DAY_OF_YEAR, -1)
        }
        return isSameDay(scan, yesterday)
    }
}
