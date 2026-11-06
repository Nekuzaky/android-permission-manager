package dev.nekuz.permhub

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import kotlin.concurrent.thread

class AppDetailActivity : Activity() {

    private lateinit var list: ListView
    private lateinit var empty: TextView
    private lateinit var progress: ProgressBar
    private lateinit var title: TextView
    private lateinit var subtitle: TextView
    private lateinit var notice: TextView
    private lateinit var adapter: RowAdapter
    private var packageId = ""
    private var generation = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        packageId = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        if (packageId.isEmpty()) {
            finish()
            return
        }
        setContentView(R.layout.activity_detail)
        list = findViewById(R.id.list)
        empty = findViewById(R.id.empty)
        progress = findViewById(R.id.progress)
        title = findViewById(R.id.title)
        subtitle = findViewById(R.id.subtitle)
        notice = findViewById(R.id.notice)
        adapter = RowAdapter(this)
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ ->
            val row = adapter.rows[position]
            if (row is Row.InfoRow && row.special != null) {
                Hub.openSpecialSettings(this, row.special, packageId)
            }
        }
        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        val icon = findViewById<ImageView>(R.id.icon)
        icon.visibility = View.VISIBLE
        Hub.bindIcon(this, icon, packageId)
        title.text = packageId
        subtitle.text = packageId

        val primary = findViewById<Button>(R.id.primaryButton)
        primary.setText(R.string.open_settings)
        primary.setOnClickListener { Hub.openAppSettings(this, packageId) }

        val appWall = Hub.appWallIntent(this)
        if (appWall != null) {
            val secondary = findViewById<Button>(R.id.secondaryButton)
            secondary.visibility = View.VISIBLE
            secondary.setText(R.string.open_appwall)
            secondary.setOnClickListener {
                Hub.appWallIntent(this)?.let { intent -> runCatching { startActivity(intent) } }
            }
        }
        val known = Inventory.result
        known.apps.firstOrNull { it.packageName == packageId }?.let { show(it, known.unknownAccess) }
    }

    override fun onResume() {
        super.onResume()
        if (packageId.isEmpty()) return
        val current = ++generation
        if (adapter.rows.isEmpty()) progress.visibility = View.VISIBLE
        val appContext = applicationContext
        thread {
            val result = runCatching { Inventory.refresh(appContext) }
            runOnUiThread {
                if (isDestroyed || current != generation) return@runOnUiThread
                progress.visibility = View.GONE
                val scan = result.getOrNull()
                val app = scan?.apps?.firstOrNull { it.packageName == packageId }
                if (scan != null && app != null) {
                    Hub.bindNotice(notice, false, scan.unknownAccess)
                    show(app, scan.unknownAccess)
                } else if (result.isSuccess) {
                    finish()
                } else if (adapter.rows.isEmpty()) {
                    empty.visibility = View.VISIBLE
                    empty.setText(R.string.load_failed)
                } else {
                    Hub.bindNotice(notice, true, Inventory.result.unknownAccess)
                }
            }
        }
    }

    private fun show(app: AppFacts, unknownAccess: Set<SpecialAccess>) {
        title.text = app.label
        subtitle.text = if (app.isSystem) {
            "${getString(R.string.app_system)} · ${app.packageName}"
        } else {
            app.packageName
        }
        adapter.rows = buildRows(app, unknownAccess)
        empty.visibility = View.GONE
    }

    private fun buildRows(app: AppFacts, unknownAccess: Set<SpecialAccess>): List<Row> = buildList {
        add(Row.Header(getString(R.string.section_findings)))
        val findings = Risk.findings(app)
        if (findings.isEmpty()) {
            add(Row.InfoRow(getString(R.string.no_findings), "", null, null))
        } else {
            findings.forEach { add(Row.InfoRow(getString(it.reason), "", it.severity, null)) }
        }

        if (app.sharedWith.isNotEmpty()) {
            add(Row.InfoRow(getString(R.string.shared_with, app.sharedWith.joinToString()), "", null, null))
        }

        val unknown = app.unknownSpecial + unknownAccess
        val shownSpecial = SpecialAccess.entries.filter {
            it in app.special || it in app.requestedSpecial || it in unknown
        }
        if (shownSpecial.isNotEmpty()) {
            add(Row.Header(getString(R.string.section_special)))
            shownSpecial.forEach { access ->
                val state = when (access) {
                    in app.special -> R.string.state_allowed
                    in unknown -> R.string.state_unknown
                    else -> R.string.state_not_allowed
                }
                add(Row.InfoRow(getString(access.title), getString(state), null, access))
            }
        }

        add(Row.Header(getString(R.string.section_permissions)))
        val requested = PermissionCategory.entries.filter { it in app.requested }
        if (requested.isEmpty()) {
            val message = if (app.unreadable) R.string.no_permissions_unreadable else R.string.no_permissions
            add(Row.InfoRow(getString(message), "", null, null))
        } else {
            requested.sortedByDescending { it in app.allowed }.forEach { category ->
                val state = if (category in app.allowed) R.string.state_allowed else R.string.state_not_allowed
                add(Row.InfoRow(getString(category.title), getString(state), null, null))
            }
        }
    }

    companion object {
        const val EXTRA_PACKAGE = "package"
    }
}
