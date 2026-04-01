package com.appkiller.tv

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var statusText: TextView
    private lateinit var btnKillSelected: Button
    private lateinit var btnKillAll: Button
    private lateinit var btnRefresh: Button
    private lateinit var btnGrantUsage: Button
    private lateinit var btnGrantAccessibility: Button
    private lateinit var adapter: AppListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.apps_recycler)
        statusText = findViewById(R.id.status_text)
        btnKillSelected = findViewById(R.id.btn_kill_selected)
        btnKillAll = findViewById(R.id.btn_kill_all)
        btnRefresh = findViewById(R.id.btn_refresh)
        btnGrantUsage = findViewById(R.id.btn_grant_usage)
        btnGrantAccessibility = findViewById(R.id.btn_grant_accessibility)

        adapter = AppListAdapter { selectedCount ->
            btnKillSelected.text = "Kill Selected ($selectedCount)"
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        btnRefresh.setOnClickListener { loadRunningApps() }

        btnKillAll.setOnClickListener {
            val packages = adapter.getAllPackages()
            if (packages.isNotEmpty()) startKilling(packages)
        }

        btnKillSelected.setOnClickListener {
            val packages = adapter.getSelectedPackages()
            if (packages.isNotEmpty()) startKilling(packages)
        }

        btnGrantUsage.setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }

        btnGrantAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    override fun onResume() {
        super.onResume()
        val hasUsage = RunningAppsHelper.hasUsageStatsPermission(this)
        val hasAccessibility = ForceStopAccessibilityService.isEnabled(this)

        btnGrantUsage.visibility = if (!hasUsage) View.VISIBLE else View.GONE
        btnGrantAccessibility.visibility = if (hasUsage && !hasAccessibility) View.VISIBLE else View.GONE

        when {
            !hasUsage -> statusText.text = "Step 1/2: Grant Usage Access to see running apps"
            !hasAccessibility -> statusText.text = "Step 2/2: Grant Accessibility permission to kill apps"
            else -> loadRunningApps()
        }
    }

    private fun loadRunningApps() {
        btnGrantUsage.visibility = View.GONE
        btnGrantAccessibility.visibility = View.GONE
        statusText.text = "Loading..."
        btnKillAll.text = "Kill All"
        btnKillSelected.text = "Kill Selected (0)"

        Thread {
            val apps = RunningAppsHelper.getRunningUserApps(this)
            runOnUiThread {
                adapter.updateApps(apps)
                statusText.text = "${apps.size} apps found in last 24h"
                btnKillAll.text = "Kill All (${apps.size})"
            }
        }.start()
    }

    private fun startKilling(packages: List<String>) {
        if (!ForceStopAccessibilityService.isEnabled(this)) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }
        statusText.text = "Killing ${packages.size} app(s)..."
        ForceStopAccessibilityService.startKilling(packages)
    }
}
