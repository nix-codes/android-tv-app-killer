package com.appkiller.tv

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppListAdapter(
    private var whitelist: Set<String> = emptySet(),
    private val onSelectionChanged: (selectedCount: Int, killableSelectedCount: Int) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_APP = 0
        private const val TYPE_HEADER = 1
    }

    private val killableApps = mutableListOf<AppItem>()
    private val protectedApps = mutableListOf<AppItem>()

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.app_icon)
        val name: TextView = view.findViewById(R.id.app_name)
        val packageName: TextView = view.findViewById(R.id.app_package)
        val checkbox: CheckBox = view.findViewById(R.id.app_checkbox)
        val lockIcon: ImageView = view.findViewById(R.id.app_lock_icon)

        init {
            view.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos == RecyclerView.NO_ID.toInt()) return@setOnClickListener
                if (getItemViewType(pos) != TYPE_APP) return@setOnClickListener
                val app = getAppAt(pos)
                app.isSelected = !app.isSelected
                checkbox.isChecked = app.isSelected
                fireSelectionCallback()
            }
        }
    }

    inner class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.section_header_text)
    }

    override fun getItemViewType(position: Int): Int =
        if (protectedApps.isNotEmpty() && position == killableApps.size) TYPE_HEADER else TYPE_APP

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderViewHolder(inflater.inflate(R.layout.item_section_header, parent, false))
        } else {
            ViewHolder(inflater.inflate(R.layout.item_app, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is HeaderViewHolder) {
            holder.text.text = "Protected (${protectedApps.size})"
            return
        }
        val app = getAppAt(position)
        holder as ViewHolder
        holder.icon.setImageDrawable(app.icon)
        holder.name.text = app.appName
        holder.packageName.text = app.packageName
        holder.checkbox.isChecked = app.isSelected
        holder.lockIcon.visibility = if (app.isWhitelisted) View.VISIBLE else View.GONE
    }

    override fun getItemCount(): Int =
        killableApps.size + if (protectedApps.isEmpty()) 0 else 1 + protectedApps.size

    private fun getAppAt(position: Int): AppItem =
        if (position < killableApps.size) killableApps[position]
        else protectedApps[position - killableApps.size - 1]

    fun updateApps(newApps: List<AppItem>) {
        killableApps.clear()
        protectedApps.clear()
        newApps.map { it.copy(isWhitelisted = it.packageName in whitelist) }
            .partition { !it.isWhitelisted }
            .let { (k, p) -> killableApps.addAll(k); protectedApps.addAll(p) }
        notifyDataSetChanged()
        fireSelectionCallback()
    }

    fun updateWhitelist(newWhitelist: Set<String>) {
        whitelist = newWhitelist
        val allApps = (killableApps + protectedApps).map { it.copy(isWhitelisted = it.packageName in whitelist, isSelected = false) }
        killableApps.clear()
        protectedApps.clear()
        allApps.partition { !it.isWhitelisted }
            .let { (k, p) -> killableApps.addAll(k); protectedApps.addAll(p) }
        notifyDataSetChanged()
        fireSelectionCallback()
    }

    fun getSelectedPackages(): List<String> =
        killableApps.filter { it.isSelected }.map { it.packageName }

    fun getAllPackages(): List<String> =
        killableApps.map { it.packageName }

    fun getCheckedUnprotectedPackages(): List<String> =
        killableApps.filter { it.isSelected }.map { it.packageName }

    fun getCheckedProtectedPackages(): List<String> =
        protectedApps.filter { it.isSelected }.map { it.packageName }

    private fun fireSelectionCallback() {
        val selectedCount = killableApps.count { it.isSelected } + protectedApps.count { it.isSelected }
        val killableSelectedCount = killableApps.count { it.isSelected }
        onSelectionChanged(selectedCount, killableSelectedCount)
    }
}
