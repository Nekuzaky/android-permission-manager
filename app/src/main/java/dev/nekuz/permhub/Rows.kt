package dev.nekuz.permhub

import android.content.Context
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView

sealed class Row {
    data class Header(val text: String) : Row()

    data class AppRow(
        val app: AppFacts,
        val subtitle: String,
        val severity: Severity?,
    ) : Row()

    data class CategoryRow(
        val title: String,
        val subtitle: String,
        val category: PermissionCategory?,
        val special: SpecialAccess?,
    ) : Row()

    data class InfoRow(
        val title: String,
        val subtitle: String,
        val severity: Severity?,
        val special: SpecialAccess?,
    ) : Row()
}

class RowAdapter(private val context: Context) : BaseAdapter() {
    var rows: List<Row> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    private val inflater = LayoutInflater.from(context)

    override fun getCount() = rows.size
    override fun getItem(position: Int) = rows[position]
    override fun getItemId(position: Int) = position.toLong()
    override fun getViewTypeCount() = 2
    override fun getItemViewType(position: Int) = if (rows[position] is Row.Header) 0 else 1
    override fun isEnabled(position: Int) = when (val row = rows[position]) {
        is Row.Header -> false
        is Row.InfoRow -> row.special != null
        else -> true
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val row = rows[position]
        if (row is Row.Header) {
            val view = convertView ?: inflater.inflate(R.layout.row_header, parent, false)
            (view as TextView).text = row.text
            return view
        }
        val view = convertView ?: inflater.inflate(R.layout.row_item, parent, false)
        val icon = view.findViewById<ImageView>(R.id.icon)
        val letter = view.findViewById<TextView>(R.id.letter)
        val title = view.findViewById<TextView>(R.id.title)
        val subtitle = view.findViewById<TextView>(R.id.subtitle)
        val tag = view.findViewById<TextView>(R.id.tag)
        val chevron = view.findViewById<View>(R.id.chevron)
        icon.tag = null
        title.maxLines = 1
        subtitle.maxLines = 2
        subtitle.ellipsize = TextUtils.TruncateAt.END
        when (row) {
            is Row.AppRow -> {
                icon.visibility = View.VISIBLE
                letter.visibility = View.GONE
                Hub.bindIcon(context, icon, row.app.packageName)
                title.text = row.app.label
                subtitle.text = row.subtitle
                subtitle.ellipsize = null
                subtitle.maxLines = Int.MAX_VALUE
                Hub.bindSeverity(tag, row.severity)
                chevron.visibility = View.VISIBLE
            }
            is Row.CategoryRow -> {
                icon.visibility = View.GONE
                letter.visibility = View.VISIBLE
                letter.text = row.title.take(1)
                title.text = row.title
                subtitle.text = row.subtitle
                Hub.bindSeverity(tag, null)
                chevron.visibility = View.VISIBLE
            }
            is Row.InfoRow -> {
                icon.visibility = View.GONE
                letter.visibility = View.GONE
                title.maxLines = 4
                title.text = row.title
                subtitle.text = row.subtitle
                Hub.bindSeverity(tag, row.severity)
                chevron.visibility = if (row.special != null) View.VISIBLE else View.GONE
            }
            is Row.Header -> Unit
        }
        subtitle.visibility = if (subtitle.text.isNullOrEmpty()) View.GONE else View.VISIBLE
        return view
    }
}
