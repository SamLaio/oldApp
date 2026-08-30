package com.samliao.findpacket

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var numberInput: EditText
    private lateinit var carrierSpinner: Spinner
    private lateinit var recentText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(32), dp(144), dp(32), dp(32))
        }

        root.addView(TextView(this).apply {
            setText(R.string.app_name)
            textSize = 28f
        })

        numberInput = EditText(this).apply {
            setHint(R.string.tracking_number_hint)
            setSingleLine(true)
        }
        root.addView(numberInput)

        carrierSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, Carriers.all)
        }
        root.addView(carrierSpinner)

        root.addView(Button(this).apply {
            isAllCaps = false
            setText(R.string.open_webview)
            setOnClickListener { openSelected(external = false) }
        })

        root.addView(Button(this).apply {
            isAllCaps = false
            setText(R.string.open_browser)
            setOnClickListener { openSelected(external = true) }
        })

        recentText = TextView(this).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(24), 0, 0)
            setOnClickListener { restoreRecent(showToast = true) }
        }
        root.addView(recentText)

        restoreRecent()
        setContentView(root)
        handleSharedText(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSharedText(intent)
    }

    private fun openSelected(external: Boolean) {
        val carrier = carrierSpinner.selectedItem as Carrier
        val number = numberInput.text.toString().trim()
        if (number.isNotEmpty()) {
            copyNumber(number)
        }
        saveRecent(number, carrierSpinner.selectedItemPosition)

        if (external) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(carrier.url)))
        } else {
            startActivity(Intent(this, WebActivity::class.java).apply {
                putExtra(WebActivity.EXTRA_TITLE, carrier.name)
                putExtra(WebActivity.EXTRA_URL, carrier.url)
                putExtra(WebActivity.EXTRA_NUMBER, number)
                putExtra(WebActivity.EXTRA_AUTOFILL_SELECTOR, carrier.autofillSelector)
            })
        }
    }

    private fun copyNumber(number: String) {
        val manager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        manager?.setPrimaryClip(ClipData.newPlainText(getString(R.string.tracking_number), number))
        Toast.makeText(this, R.string.number_copied, Toast.LENGTH_SHORT).show()
    }

    private fun saveRecent(number: String, carrierIndex: Int) {
        getSharedPreferences(PREFS, MODE_PRIVATE)
            .edit()
            .putString(KEY_NUMBER, number)
            .putInt(KEY_CARRIER, carrierIndex)
            .apply()
        restoreRecent()
    }

    private fun restoreRecent(showToast: Boolean = false) {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val number = prefs.getString(KEY_NUMBER, "").orEmpty()
        val index = prefs.getInt(KEY_CARRIER, 0).coerceIn(Carriers.all.indices)
        carrierSpinner.setSelection(index)
        numberInput.setText(number)
        numberInput.setSelection(number.length)
        recentText.text = if (number.isEmpty()) "" else getString(R.string.recent_query, Carriers.all[index].name, number)
        recentText.isEnabled = number.isNotEmpty()
        if (showToast && number.isNotEmpty()) {
            Toast.makeText(this, R.string.recent_query_restored, Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleSharedText(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type?.startsWith("text/") != true) {
            return
        }

        val sharedText = listOfNotNull(
            intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(),
            intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString(),
        ).joinToString(" ")
        val number = extractTrackingNumber(sharedText) ?: return

        numberInput.setText(number)
        numberInput.setSelection(number.length)
        Toast.makeText(this, R.string.shared_number_filled, Toast.LENGTH_SHORT).show()
    }

    private fun extractTrackingNumber(text: String): String? {
        return Regex("[A-Za-z0-9][A-Za-z0-9 -]{5,39}")
            .findAll(text)
            .map { match -> match.value to match.value.replace(Regex("[^A-Za-z0-9]"), "") }
            .filter { (raw, value) ->
                !raw.contains("http", ignoreCase = true) &&
                    !raw.contains("www", ignoreCase = true) &&
                    value.length in 8..30 &&
                    value.count(Char::isDigit) >= 5
            }
            .maxByOrNull { (_, value) -> value.length }
            ?.second
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val PREFS = "find_packet"
        const val KEY_NUMBER = "number"
        const val KEY_CARRIER = "carrier"
    }
}
