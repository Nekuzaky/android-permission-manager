package dev.nekuz.permhub

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import kotlin.concurrent.thread

class AccessDetailActivity : Activity() {

    private lateinit var list: ListView
    private lateinit var empty: TextView
    private lateinit var progress: ProgressBar
    private lateinit var subtitle: TextView
    private lateinit var notice: TextView
    private lateinit var adapter: RowAdapter
    private var category: PermissionCategory? = null
    private var special: SpecialAccess? = null
    private var showSystem = false
    private var generation = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        category = intent.getStringExtra(EXTRA_CATEGORY)?.let { name ->
            PermissionCategory.entries.firstOrNull { it.name == name }
        }
        special = intent.getStringExtra(EXTRA_SPECIAL)?.let { name ->
            SpecialAccess.entries.firstOrNull { it.name == name }
        }
        showSystem = intent.getBooleanExtra(EXTRA_SHOW_SYSTEM, false)
        val titleRes = category?.title ?: special?.title
        if (titleRes == null) {
            finish()
            return
        }
        setContentView(R.layout.activity_detail)
        list = findViewById(R.id.list)
        empty = findViewById(R.id.empty)
        progress = findViewById(R.id.progress)
        subtitle = findViewById(R.id.subtitle)
        notice = findViewById(R.id.notice)
        adapter = RowAdapter(this)
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ ->
            val row = adapter.rows[position]
            if (row is Row.AppRow) {
                startActivity(
                    Intent(this, AppDetailActivity::class.java)
                        .putExtra(AppDetailActivity.EXTRA_PACKAGE, row.app.packageName)
                )
            }
        }
        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        findViewById<TextView>(R.id.title).setText(titleRes)

        val primary = findViewById<Button>(R.id.primaryButton)
        val access = special
        if (access != null) {
            primary.setText(R.string.open_access_settings)
            primary.setOnClickListener { Hub.openSpecialSettings(this, access, null) }
        } else {
            primary.visibility = View.GONE
        }
        show(Inventory.result)
    }

    override fun onResume() {
        super.onResume()
        if (category == null && special == null) return
        val current = ++generation
        if (adapter.rows.isEmpty()) progress.visibility = View.VISIBLE
        val appContext = applicationContext
        thread {
            val result = runCatching { Inventory.refresh(appContext) }
            runOnUiThread {
                if (isDestroyed || current != generation) return@runOnUiThread
                progress.visibility = View.GONE
                result.onSuccess {
                    Hub.bindNotice(notice, false, it.unknownAccess)
                    show(it)
                }.onFailure {
                    if (adapter.rows.isEmpty()) {
                        empty.visibility = View.VISIBLE
                        empty.setText(R.string.load_failed)
                    } else {
                        Hub.bindNotice(notice, true, Inventory.result.unknownAccess)
                    }
                }
            }
        }
    }

    private fun show(scan: ScanResult) {
        val visible = scan.apps.filter { showSystem || !it.isSystem }
        val allowed = visible.filter(::isAllowed)
        val requested = visible.filter { !isAllowed(it) && isRequested(it) }
        val unknown = special?.let { it in scan.unknownAccess || visible.any { app -> it in app.unknownSpecial } } ?: false
        subtitle.text = if (unknown) {
            getString(R.string.state_unknown)
        } else {
            getString(R.string.access_detail_count, allowed.size, requested.size)
        }
        adapter.rows = buildList {
            if (allowed.isNotEmpty()) {
                add(Row.Header(getString(R.string.section_allowed)))
                allowed.forEach { add(appRow(it)) }
            }
            if (requested.isNotEmpty()) {
                add(Row.Header(getString(R.string.section_requested)))
                requested.forEach { add(appRow(it)) }
            }
        }
        empty.visibility = if (adapter.rows.isEmpty()) View.VISIBLE else View.GONE
        empty.setText(if (unknown) R.string.state_unknown else R.string.empty_access)
    }

    private fun appRow(app: AppFacts) = Row.AppRow(app, app.packageName, Risk.level(app))

    private fun isAllowed(app: AppFacts): Boolean {
        category?.let { return it in app.allowed }
        return special?.let { it in app.special } ?: false
    }

    private fun isRequested(app: AppFacts): Boolean {
        category?.let { return it in app.requested }
        return special?.let { it in app.requestedSpecial } ?: false
    }

    companion object {
        const val EXTRA_CATEGORY = "category"
        const val EXTRA_SPECIAL = "special"
        const val EXTRA_SHOW_SYSTEM = "show_system"
    }
}
