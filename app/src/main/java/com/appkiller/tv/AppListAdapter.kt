package com.appkiller.tv

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppListAdapter(
    private val onSelectionChanged: (Int) -> Unit
) : RecyclerView.Adapter<AppListAdapter.ViewHolder>() {

    private val apps = mutableListOf<AppItem>()

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.app_icon)
        val name: TextView = view.findViewById(R.id.app_name)
        val packageName: TextView = view.findViewById(R.id.app_package)
        val checkbox: CheckBox = view.findViewById(R.id.app_checkbox)

        init {
            view.setOnClickListener {
                val app = apps[adapterPosition]
                app.isSelected = !app.isSelected
                checkbox.isChecked = app.isSelected
                onSelectionChanged(apps.count { it.isSelected })
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
    }

    override fun getItemCount() = apps.size

    fun updateApps(newApps: List<AppItem>) {
        apps.clear()
        apps.addAll(newApps)
        notifyDataSetChanged()
        onSelectionChanged(0)
    }

    fun getSelectedPackages(): List<String> = apps.filter { it.isSelected }.map { it.packageName }

    fun getAllPackages(): List<String> = apps.map { it.packageName }
}
