package com.santhu.keepmyphoneout

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class HistoryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        btnBack.setOnClickListener {
            finish()
        }

        val btnClear = findViewById<ImageButton>(R.id.btnClearHistory)
        btnClear.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Clear History")
                .setMessage("Are you sure you want to delete all focus history?")
                .setPositiveButton("Clear") { _, _ ->
                    Utils.clearHistory(this)
                    loadSessionHistory()
                }
                .setNeutralButton("Cancel", null)
                .show()
        }

        loadSessionHistory()
    }

    private fun loadSessionHistory() {
        val historyContainer = findViewById<LinearLayout>(R.id.historyContainerScreen)
        historyContainer.removeAllViews()
        
        val history = Utils.getSessionHistoryRecords(this)
        if (history.isEmpty()) {
            val tv = TextView(this)
            tv.text = "No history record found yet.\nStart your first session!"
            tv.setTextColor(getColor(R.color.text_secondary))
            tv.gravity = android.view.Gravity.CENTER
            tv.setPadding(32, 64, 32, 64)
            historyContainer.addView(tv)
        } else {
            for (record in history) {
                val itemRow = LinearLayout(this)
                itemRow.orientation = LinearLayout.HORIZONTAL
                itemRow.gravity = android.view.Gravity.CENTER_VERTICAL
                itemRow.isClickable = true
                val typedValue = android.util.TypedValue()
                theme.resolveAttribute(android.R.attr.selectableItemBackground, typedValue, true)
                itemRow.setBackgroundResource(typedValue.resourceId)

                val tv = TextView(this)
                tv.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                tv.text = "• ${record.durationMinutes} mins on ${record.dateString}"
                tv.setTextColor(getColor(R.color.text_primary))
                tv.textSize = 15f
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    tv.letterSpacing = 0.01f
                }
                tv.setPadding(24, 28, 8, 28)
                tv.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                
                val btnDelete = ImageButton(this)
                btnDelete.layoutParams = LinearLayout.LayoutParams(TypedValue_dpToPx(48), TypedValue_dpToPx(48))
                btnDelete.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                btnDelete.background = null
                btnDelete.imageTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.text_tertiary))
                btnDelete.setOnClickListener {
                     Utils.removeHistoryItem(this, record.timestamp)
                     loadSessionHistory()
                }

                itemRow.setOnClickListener {
                    // Restore this session's settings
                    Utils.setAllowedApps(this, record.allowedApps.toSet())
                    val intent = android.content.Intent()
                    intent.putExtra("restore_duration_mins", record.durationMinutes)
                    setResult(android.app.Activity.RESULT_OK, intent)
                    finish()
                }

                itemRow.addView(tv)
                itemRow.addView(btnDelete)
                historyContainer.addView(itemRow)
                
                // Add a premium divider
                val divider = View(this)
                val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                params.setMargins(16, 0, 16, 0)
                divider.layoutParams = params
                divider.setBackgroundColor(getColor(R.color.surface_elevated))
                historyContainer.addView(divider)
            }
        }
    }

    private fun TypedValue_dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
