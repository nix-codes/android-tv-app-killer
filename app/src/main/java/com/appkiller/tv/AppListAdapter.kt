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
) : RecyclerView.Adapter<AppListAdapter.ViewHolder>() {

    private val apps = mutableListOf<AppItem>()

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.app_icon)
        val name: TextView = view.findViewById(R.id.app_name)
        val packageName: TextView = view.findViewById(R.id.app_package)
        val checkbox: CheckBox = view.findViewById(R.id.app_checkbox)
        val lockIcon: ImageView = view.findViewById(R.id.app_lock_icon)

        init {
            view.setOnClickListener {
                val app = apps[adapterPosition]
                app.isSelected = !app.isSelected
                checkbox.isChecked = app.isSelected
                fireSelectionCallback()
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = apps[position]
        holder.icon.setImageDrawable(app.icon)
        holder.name.text = app.appName
        holder.packageName.text = app.packageName
        holder.checkbox.isChecked = app.isSelected
        holder.lockIcon.visibility = if (app.isWhitelisted) View.VISIBLE else View.GONE
    }

    override fun getItemCount() = apps.size

    fun updateApps(newApps: List<AppItem>) {
        apps.clear()
        apps.addAll(newApps.map { it.copy(isWhitelisted = it.packageName in whitelist) })
        notifyDataSetChanged()
        fireSelectionCallback()
    }

    fun updateWhitelist(newWhitelist: Set<String>) {
        whitelist = newWhitelist
        apps.forEach { it.isWhitelisted = it.packageName in whitelist }
        notifyDataSetChanged()
        fireSelectionCallback()
    }

    fun getSelectedPackages(): List<String> =
        apps.filter { it.isSelected && !it.isWhitelisted }.map { it.packageName }

    fun getAllPackages(): List<String> =
        apps.filter { !it.isWhitelisted }.map { it.packageName }

    fun getCheckedUnprotectedPackages(): List<String> =
        apps.filter { it.isSelected && !it.isWhitelisted }.map { it.packageName }

    fun getCheckedProtectedPackages(): List<String> =
        apps.filter { it.isSelected && it.isWhitelisted }.map { it.packageName }

    private fun fireSelectionCallback() {
        val selectedCount = apps.count { it.isSelected }
        val killableSelectedCount = apps.count { it.isSelected && !it.isWhitelisted }
        onSelectionChanged(selectedCount, killableSelectedCount)
    }
}
