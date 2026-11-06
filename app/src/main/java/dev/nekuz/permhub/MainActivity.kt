package dev.nekuz.permhub

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.Switch
import android.widget.TextView
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private enum class Tab { REVIEW, ACCESS, APPS }

    private lateinit var heroCount: TextView
    private lateinit var heroLabel: TextView
    private lateinit var heroDetail: TextView
    private lateinit var tabReview: Button
    private lateinit var tabAccess: Button
    private lateinit var tabApps: Button
    private lateinit var search: EditText
    private lateinit var systemSwitch: Switch
    private lateinit var systemRow: View
    private lateinit var list: ListView
    private lateinit var empty: TextView
    private lateinit var progress: ProgressBar
    private lateinit var notice: TextView
    private lateinit var adapter: RowAdapter

    private var apps: List<AppFacts> = emptyList()
    private var unknownAccess: Set<SpecialAccess> = emptySet()
    private var outdated = false
    private var tab = Tab.REVIEW
    private var showSystem = false
    private var loaded = false
    private var failed = false
    private var generation = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val hero = findViewById<View>(R.id.heroCard)
        hero.clipToOutline = true
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) hero.visibility = View.GONE
        systemRow = findViewById(R.id.systemRow)
        heroCount = findViewById(R.id.heroCount)
        heroLabel = findViewById(R.id.heroLabel)
        heroDetail = findViewById(R.id.heroDetail)
        tabReview = findViewById(R.id.tabReview)
        tabAccess = findViewById(R.id.tabAccess)
        tabApps = findViewById(R.id.tabApps)
        search = findViewById(R.id.search)
        systemSwitch = findViewById(R.id.systemSwitch)
        list = findViewById(R.id.list)
        empty = findViewById(R.id.empty)
        progress = findViewById(R.id.progress)
        notice = findViewById(R.id.notice)

        tab = savedInstanceState?.getString(STATE_TAB)?.let { name ->
            Tab.entries.firstOrNull { it.name == name }
        } ?: Tab.REVIEW
        showSystem = savedInstanceState?.getBoolean(STATE_SYSTEM) ?: false
        systemSwitch.isChecked = showSystem

        adapter = RowAdapter(this)
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ -> onRowClick(adapter.rows[position]) }
        tabReview.setOnClickListener { selectTab(Tab.REVIEW) }
        tabAccess.setOnClickListener { selectTab(Tab.ACCESS) }
        tabApps.setOnClickListener { selectTab(Tab.APPS) }
        systemSwitch.setOnCheckedChangeListener { _, checked ->
            showSystem = checked
            render()
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = render()
        })
        findViewById<View>(R.id.infoButton).setOnClickListener { showInfo() }
        updateTabs()
        renderHero()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_TAB, tab.name)
        outState.putBoolean(STATE_SYSTEM, showSystem)
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        val current = ++generation
        if (!loaded) progress.visibility = View.VISIBLE
        val appContext = applicationContext
        thread {
            val result = runCatching { Inventory.refresh(appContext) }
            runOnUiThread {
                if (isDestroyed || current != generation) return@runOnUiThread
                progress.visibility = View.GONE
                result.onSuccess {
                    apps = it.apps
                    unknownAccess = it.unknownAccess
                    loaded = true
                    failed = false
                    outdated = false
                }.onFailure {
                    failed = !loaded
                    outdated = loaded
                }
                Hub.bindNotice(notice, outdated, unknownAccess)
                renderHero()
                render()
            }
        }
    }

    private fun selectTab(selected: Tab) {
        tab = selected
        updateTabs()
        render()
        list.setSelection(0)
    }

    private fun updateTabs() {
        styleTab(tabReview, tab == Tab.REVIEW)
        styleTab(tabAccess, tab == Tab.ACCESS)
        styleTab(tabApps, tab == Tab.APPS)
        systemRow.visibility = if (tab == Tab.REVIEW) View.GONE else View.VISIBLE
    }

    private fun styleTab(button: Button, selected: Boolean) {
        button.setBackgroundResource(if (selected) R.drawable.bg_button_primary else R.drawable.bg_button_outline)
        button.setTextColor(getColor(if (selected) R.color.on_brand else R.color.text_primary))
    }

    private fun renderHero() {
        if (!loaded) {
            heroCount.text = "…"
            heroLabel.text = getString(R.string.hero_loading)
            heroDetail.text = null
            return
        }
        val userApps = apps.filter { !it.isSystem }
        val toReview = userApps.count { Risk.level(it) == Severity.HIGH }
        heroCount.text = toReview.toString()
        heroLabel.text = resources.getQuantityText(R.plurals.review_count, toReview)
        heroDetail.text = getString(R.string.hero_scanned, apps.size)
    }

    private fun visibleApps(): List<AppFacts> {
        val query = search.text.toString().trim()
        return apps.filter { app ->
            (showSystem || !app.isSystem) &&
                (query.isEmpty() ||
                    app.label.contains(query, ignoreCase = true) ||
                    app.packageName.contains(query, ignoreCase = true))
        }
    }

    private fun render() {
        if (!loaded) {
            adapter.rows = emptyList()
            empty.visibility = if (failed) View.VISIBLE else View.GONE
            empty.setText(R.string.load_failed)
            return
        }
        val rows = when (tab) {
            Tab.REVIEW -> reviewRows()
            Tab.ACCESS -> accessRows()
            Tab.APPS -> appRows()
        }
        adapter.rows = rows
        empty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        val hasQuery = search.text.toString().isNotBlank()
        empty.setText(
            when {
                hasQuery -> R.string.empty_search
                tab == Tab.REVIEW -> R.string.empty_review
                else -> R.string.empty_apps
            }
        )
    }

    private fun reviewRows(): List<Row> = visibleApps()
        .mapNotNull { app ->
            val findings = Risk.findings(app)
            val top = findings.firstOrNull() ?: return@mapNotNull null
            if (top.severity != Severity.HIGH) return@mapNotNull null
            Row.AppRow(app, "${getString(top.reason)}\n${app.packageName}", top.severity)
        }

    private fun appRows(): List<Row> = visibleApps()
        .map { app ->
            val severity = Risk.level(app)
            Row.AppRow(app, app.packageName, severity)
        }
        .sortedWith(compareByDescending<Row.AppRow> { it.severity?.ordinal ?: -1 }.thenBy { it.app.label.lowercase() })

    private fun accessRows(): List<Row> {
        val query = search.text.toString().trim()
        val visible = apps.filter { showSystem || !it.isSystem }
        val matches = { title: String -> query.isEmpty() || title.contains(query, ignoreCase = true) }
        val permissionRows = PermissionCategory.availableOn(Build.VERSION.SDK_INT)
            .map { category ->
                val title = getString(category.title)
                val count = visible.count { category in it.allowed }
                Row.CategoryRow(title, getString(R.string.access_count, count), category, null)
            }
            .filter { matches(it.title) }
        val specialRows = SpecialAccess.entries
            .map { access ->
                val title = getString(access.title)
                val count = visible.count { access in it.special }
                val subtitle = if (access in unknownAccess) {
                    getString(R.string.state_unknown)
                } else {
                    getString(R.string.access_count, count)
                }
                Row.CategoryRow(title, subtitle, null, access)
            }
            .filter { matches(it.title) }
        return buildList {
            if (specialRows.isNotEmpty()) {
                add(Row.Header(getString(R.string.section_special)))
                addAll(specialRows)
            }
            if (permissionRows.isNotEmpty()) {
                add(Row.Header(getString(R.string.section_permissions)))
                addAll(permissionRows)
            }
        }
    }

    private fun onRowClick(row: Row) {
        when (row) {
            is Row.AppRow -> startActivity(
                Intent(this, AppDetailActivity::class.java)
                    .putExtra(AppDetailActivity.EXTRA_PACKAGE, row.app.packageName)
            )
            is Row.CategoryRow -> startActivity(
                Intent(this, AccessDetailActivity::class.java)
                    .putExtra(AccessDetailActivity.EXTRA_CATEGORY, row.category?.name)
                    .putExtra(AccessDetailActivity.EXTRA_SPECIAL, row.special?.name)
                    .putExtra(AccessDetailActivity.EXTRA_SHOW_SYSTEM, showSystem)
            )
            else -> Unit
        }
    }

    private fun showInfo() {
        AlertDialog.Builder(this)
            .setTitle(R.string.info)
            .setMessage(R.string.info_message)
            .setPositiveButton(R.string.close, null)
            .show()
    }

    private companion object {
        const val STATE_TAB = "tab"
        const val STATE_SYSTEM = "system"
    }
}
