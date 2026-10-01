package com.ead.solargrid.ui.operator

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.ead.solargrid.R
import com.ead.solargrid.database.SessionManager
import com.ead.solargrid.ui.auth.LoginActivity

/** Soft background + strong foreground pairs used for icon badges and status chips. */
enum class OperatorTone(@ColorRes val background: Int, @ColorRes val foreground: Int) {
    AMBER(R.color.op_amber_bg, R.color.op_amber_fg),
    GREEN(R.color.op_green_bg, R.color.op_green_fg),
    BLUE(R.color.op_blue_bg, R.color.op_blue_fg),
    YELLOW(R.color.op_yellow_bg, R.color.op_yellow_fg),
    ORANGE(R.color.op_orange_bg, R.color.op_orange_fg),
    NEUTRAL(R.color.op_neutral_bg, R.color.op_neutral_fg),
    RED(R.color.op_red_bg, R.color.op_red_fg)
}

/** How each reservation status is labelled and coloured on the operator screens. */
enum class OperatorReservationStatus(val apiValue: String, @StringRes val label: Int, val tone: OperatorTone) {
    PENDING("Pending", R.string.booking_status_pending, OperatorTone.AMBER),
    APPROVED("Approved", R.string.booking_status_approved, OperatorTone.GREEN),
    COMPLETED("Completed", R.string.booking_status_completed, OperatorTone.BLUE),
    CANCELLED("Cancelled", R.string.booking_status_cancelled, OperatorTone.NEUTRAL),
    REJECTED("Rejected", R.string.booking_status_rejected, OperatorTone.RED);

    companion object {
        fun from(value: String?): OperatorReservationStatus? =
            entries.firstOrNull { it.apiValue.equals(value?.trim(), ignoreCase = true) }
    }
}

object OperatorScreen {

    /**
     * Edge-to-edge with light bars: [topBar] is padded for the status bar, [bottom] (usually the
     * scrolling list, with clipToPadding=false) for the navigation bar, and the root for side insets.
     */
    fun applyInsets(activity: Activity, root: View, topBar: View, bottom: View) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        WindowCompat.getInsetsController(activity.window, root).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        val baseTop = topBar.paddingTop
        val baseBottom = bottom.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.updatePadding(left = bars.left, right = bars.right)
            topBar.updatePadding(top = baseTop + bars.top)
            bottom.updatePadding(bottom = baseBottom + bars.bottom)
            insets
        }
    }

    fun applyTone(badge: View, iconView: ImageView, @DrawableRes icon: Int, tone: OperatorTone) {
        val context = badge.context
        badge.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, tone.background))
        iconView.setImageResource(icon)
        iconView.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, tone.foreground))
    }

    /** A pill label tinted by [tone]; the view's background must be bg_operator_role_chip. */
    fun applyChip(chip: TextView, text: CharSequence, tone: OperatorTone) {
        val context = chip.context
        chip.text = text
        chip.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, tone.background))
        chip.setTextColor(ContextCompat.getColor(context, tone.foreground))
    }

    /** Clears the session and returns to login, as the dashboard does on a 401. */
    fun sessionExpired(activity: Activity) {
        Toast.makeText(activity, R.string.operator_session_expired, Toast.LENGTH_LONG).show()
        SessionManager(activity).logout()
        activity.startActivity(
            Intent(activity, LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        activity.finish()
    }
}
