package dev.opencodegometer

import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import dev.opencodegometer.data.ApiResult
import dev.opencodegometer.data.ErrorCode
import dev.opencodegometer.data.GoApi
import dev.opencodegometer.data.SecureStore
import dev.opencodegometer.data.SnapshotStore
import dev.opencodegometer.data.UsageData
import dev.opencodegometer.data.UsageSnapshot
import dev.opencodegometer.databinding.ActivityMainBinding
import dev.opencodegometer.util.ErrorMessages
import dev.opencodegometer.util.Formatters
import dev.opencodegometer.util.labelRes
import dev.opencodegometer.widget.GoMeterSmallWidgetProvider
import dev.opencodegometer.widget.GoMeterWidgetProvider
import dev.opencodegometer.widget.WidgetUpdater
import dev.opencodegometer.work.RefreshScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var binding: ActivityMainBinding
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var isWidgetConfig = false
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        isWidgetConfig = intent.action == AppWidgetManager.ACTION_APPWIDGET_CONFIGURE ||
            widgetId != AppWidgetManager.INVALID_APPWIDGET_ID
        if (isWidgetConfig) {
            setResult(RESULT_CANCELED)
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.rootScroll) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(0, bars.top, 0, maxOf(bars.bottom, ime.bottom))
            insets
        }

        binding.apiKeyInput.hint = getString(R.string.api_key_hint)
        updateKeyLabel()

        binding.showKey.setOnCheckedChangeListener { _, checked ->
            val selection = binding.apiKeyInput.selectionEnd.coerceAtLeast(0)
            binding.apiKeyInput.inputType = if (checked) {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            val length = binding.apiKeyInput.text?.length ?: 0
            binding.apiKeyInput.setSelection(selection.coerceAtMost(length))
        }

        binding.saveButton.setOnClickListener { saveAndTest() }
        binding.refreshButton.setOnClickListener { refreshNow() }
        binding.deleteButton.setOnClickListener { confirmDelete() }
        binding.consoleLink.setOnClickListener {
            runCatching {
                startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://opencode.ai/console/go"))
                )
            }
        }

        if (isWidgetConfig) {
            binding.deleteButton.visibility = View.GONE
            binding.refreshButton.visibility = View.GONE
            binding.saveButton.text = getString(R.string.save_and_add_widget)
        }

        if (BuildConfig.DEBUG && intent.getBooleanExtra("demo", false)) {
            val demo = UsageSnapshot.demo(System.currentTimeMillis())
            SnapshotStore.save(this, demo)
            WidgetUpdater.updateAll(this)
        }

        renderSnapshot(SnapshotStore.load(this))
        scheduleIfKeyExists()
        if (savedInstanceState == null) {
            maybeRefreshOnLaunch()
        }
    }

    private fun maybeRefreshOnLaunch() {
        if (isWidgetConfig) return
        if (BuildConfig.DEBUG && intent.getBooleanExtra("demo", false)) return
        val key = SecureStore.getApiKey(this) ?: return
        val snapshot = SnapshotStore.load(this)
        val fetchedAt = snapshot?.fetchedAt
        val stale = fetchedAt == null || System.currentTimeMillis() - fetchedAt > 5 * 60_000L
        if (stale) {
            fetchAndRender(key, fromSave = false, finishOnSuccess = false)
        }
    }

    private fun saveAndTest() {
        if (busy) return
        val typed = binding.apiKeyInput.text?.toString()?.trim().orEmpty()
        val existing = SecureStore.getApiKey(this)
        val key = typed.ifBlank { existing }

        if (key.isNullOrBlank()) {
            showMessage(getString(R.string.key_required), isError = true)
            return
        }

        if (typed.isNotBlank()) {
            if (!SecureStore.setApiKey(this, typed)) {
                showMessage(getString(R.string.key_save_failed), isError = true)
                return
            }
            binding.apiKeyInput.setText("")
            binding.showKey.isChecked = false
            updateKeyLabel()
            showMessage(getString(R.string.key_saved), isError = false)
        } else {
            showMessage(getString(R.string.using_saved_key), isError = false)
        }

        fetchAndRender(key, fromSave = true, finishOnSuccess = isWidgetConfig)
    }

    private fun refreshNow() {
        if (busy) return
        val key = SecureStore.getApiKey(this)
        if (key.isNullOrBlank()) {
            showMessage(getString(R.string.key_required), isError = true)
            return
        }
        fetchAndRender(key, fromSave = false, finishOnSuccess = false)
    }

    private fun fetchAndRender(key: String, fromSave: Boolean, finishOnSuccess: Boolean) {
        setBusy(true)
        if (fromSave) {
            showMessage(getString(R.string.testing), isError = false)
        }

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { GoApi.fetch(key) }
            setBusy(false)

            when (result) {
                is ApiResult.Success -> {
                    val snapshot = UsageSnapshot.success(result.data, System.currentTimeMillis())
                    SnapshotStore.save(this@MainActivity, snapshot)
                    WidgetUpdater.updateAll(this@MainActivity)
                    renderSnapshot(snapshot)
                    if (hasWidgets()) RefreshScheduler.schedule(this@MainActivity)
                    if (fromSave) {
                        showMessage(getString(R.string.connection_ok), isError = false)
                    } else {
                        binding.message.text = ""
                    }
                    updateKeyLabel()
                    if (finishOnSuccess) {
                        finishWithWidgetResult()
                    }
                }

                is ApiResult.Failure -> {
                    val previous = SnapshotStore.load(this@MainActivity)
                    val snapshot = UsageSnapshot.failure(
                        previous,
                        result.code,
                        System.currentTimeMillis()
                    )
                    SnapshotStore.save(this@MainActivity, snapshot)
                    WidgetUpdater.updateAll(this@MainActivity)
                    renderSnapshot(snapshot)

                    val label = ErrorMessages.label(this@MainActivity, result.code)
                        ?: getString(R.string.connection_failed)
                    val message = if (fromSave) {
                        getString(R.string.key_saved_but_failed) + " " + label
                    } else {
                        label
                    }
                    showMessage(message, isError = true)

                    when (result.code) {
                        ErrorCode.UNAUTHORIZED, ErrorCode.NO_SUBSCRIPTION ->
                            RefreshScheduler.cancel(this@MainActivity)
                        else -> if (hasWidgets()) {
                            RefreshScheduler.schedule(this@MainActivity)
                        }
                    }

                    updateKeyLabel()

                    if (finishOnSuccess &&
                        result.code != ErrorCode.UNAUTHORIZED &&
                        result.code != ErrorCode.NO_SUBSCRIPTION
                    ) {
                        Toast.makeText(
                            this@MainActivity,
                            R.string.key_saved_but_failed,
                            Toast.LENGTH_LONG
                        ).show()
                        finishWithWidgetResult()
                    }
                }
            }
        }
    }

    private fun renderSnapshot(snapshot: UsageSnapshot?) {
        val data = snapshot?.data
        val planName = getString(
            if (data?.isGoPlus == true) R.string.plan_go_plus else R.string.plan_go
        )
        val title = buildString {
            append(
                getString(
                    R.string.status_title_with_plan,
                    planName,
                    getString(R.string.status_card_title)
                )
            )
            if (snapshot?.isDemo == true) {
                append(" · ")
                append(getString(R.string.status_demo))
            }
        }
        binding.statusTitle.text = title

        if (data == null) {
            binding.statusBig.text = getString(R.string.reset_unknown)
            binding.statusBig.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            binding.statusRows.text = getString(R.string.status_no_data)
            binding.statusFooter.text = buildFooter(snapshot, null)
            binding.statusFooter.setTextColor(
                ContextCompat.getColor(
                    this,
                    if (snapshot?.error != null) R.color.danger else R.color.text_secondary
                )
            )
            return
        }

        val used = data.mostUsed().second.usedRounded
        binding.statusBig.text = Formatters.percent(used)
        binding.statusBig.setTextColor(
            ContextCompat.getColor(
                this,
                when {
                    used < 50 -> R.color.accent_dark
                    used < 75 -> R.color.warning
                    else -> R.color.danger
                }
            )
        )

        binding.statusRows.text = data.all().joinToString("\n") { (kind, meter) ->
            getString(
                R.string.usage_row,
                getString(kind.labelRes()),
                meter.usedRounded,
                Formatters.resetLabel(this, meter.resetsAt)
            )
        }

        binding.statusFooter.text = buildFooter(snapshot, data)
        binding.statusFooter.setTextColor(
            ContextCompat.getColor(
                this,
                if (snapshot?.error != null) R.color.danger else R.color.text_secondary
            )
        )
    }

    private fun buildFooter(snapshot: UsageSnapshot?, data: UsageData?): String {
        val lines = mutableListOf<String>()
        val fetchedAt = snapshot?.fetchedAt
        if (fetchedAt != null) {
            lines += getString(R.string.status_last_updated, Formatters.dateTime(fetchedAt))
        } else if (snapshot?.error == null) {
            lines += getString(R.string.status_never_updated)
        }
        snapshot?.error?.let { code ->
            val label = ErrorMessages.label(this, code) ?: getString(R.string.connection_failed)
            lines += getString(R.string.status_error, label)
        }
        data?.renewsAt?.let {
            lines += getString(R.string.monthly_renewal, Formatters.dateTime(it))
        }
        if (data?.cancelAtPeriodEnd == true) {
            lines += getString(R.string.cancel_at_period_end)
        }
        return lines.joinToString("\n")
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_key_confirm_title)
            .setMessage(R.string.delete_key_confirm_message)
            .setPositiveButton(R.string.delete) { _, _ ->
                SecureStore.clear(this)
                SnapshotStore.clear(this)
                RefreshScheduler.cancel(this)
                binding.apiKeyInput.setText("")
                binding.showKey.isChecked = false
                updateKeyLabel()
                showMessage("", isError = false)
                renderSnapshot(null)
                WidgetUpdater.updateAll(this)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun scheduleIfKeyExists() {
        if (!hasWidgets()) return
        if (SecureStore.getApiKey(this).isNullOrBlank()) return
        val snapshot = SnapshotStore.load(this)
        if (snapshot?.error == ErrorCode.UNAUTHORIZED ||
            snapshot?.error == ErrorCode.NO_SUBSCRIPTION
        ) {
            return
        }
        RefreshScheduler.schedule(this)
    }

    private fun hasWidgets(): Boolean {
        val manager = AppWidgetManager.getInstance(this)
        return manager.getAppWidgetIds(
            ComponentName(this, GoMeterWidgetProvider::class.java)
        ).isNotEmpty() || manager.getAppWidgetIds(
            ComponentName(this, GoMeterSmallWidgetProvider::class.java)
        ).isNotEmpty()
    }

    private fun updateKeyLabel() {
        val saved = SecureStore.getApiKey(this) != null
        binding.apiKeyLabel.text = getString(
            if (saved) R.string.api_key_label_saved else R.string.api_key_label
        )
    }

    private fun setBusy(value: Boolean) {
        busy = value
        binding.saveButton.isEnabled = !value
        binding.refreshButton.isEnabled = !value
        binding.deleteButton.isEnabled = !value
        binding.apiKeyInput.isEnabled = !value
        binding.showKey.isEnabled = !value
    }

    private fun showMessage(text: String, isError: Boolean) {
        binding.message.text = text
        binding.message.setTextColor(
            ContextCompat.getColor(
                this,
                if (isError) R.color.danger else R.color.text_secondary
            )
        )
    }

    private fun finishWithWidgetResult() {
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        setResult(RESULT_OK, result)
        finish()
    }
}
