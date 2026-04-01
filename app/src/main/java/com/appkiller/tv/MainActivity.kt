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
    private lateinit var btnProtect: Button
    private lateinit var btnUnprotect: Button
    private lateinit var adapter: AppListAdapter
    private var whitelist: MutableSet<String> = mutableSetOf()

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
        btnProtect = findViewById(R.id.btn_protect)
        btnUnprotect = findViewById(R.id.btn_unprotect)

        whitelist = WhitelistRepository.load(this).toMutableSet()

        adapter = AppListAdapter(whitelist) { _, killableSelectedCount ->
            updateButtonLabels(killableSelectedCount)
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

        btnProtect.setOnClickListener {
            val toProtect = adapter.getCheckedUnprotectedPackages()
            if (toProtect.isEmpty()) return@setOnClickListener
            whitelist.addAll(toProtect)
            WhitelistRepository.save(this, whitelist)
            adapter.updateWhitelist(whitelist)
            btnKillAll.text = "Kill All (${adapter.getAllPackages().size})"
        }

        btnUnprotect.setOnClickListener {
            val toUnprotect = adapter.getCheckedProtectedPackages()
            if (toUnprotect.isEmpty()) return@setOnClickListener
            whitelist.removeAll(toUnprotect.toSet())
            WhitelistRepository.save(this, whitelist)
            adapter.updateWhitelist(whitelist)
            btnKillAll.text = "Kill All (${adapter.getAllPackages().size})"
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
        btnProtect.text = "Protect (0)"
        btnProtect.isEnabled = false
        btnUnprotect.text = "Unprotect (0)"
        btnUnprotect.isEnabled = false

        Thread {
            val apps = RunningAppsHelper.getRunningUserApps(this)
            runOnUiThread {
                adapter.updateApps(apps)
                val killableCount = adapter.getAllPackages().size
                val protectedCount = apps.size - killableCount
                statusText.text = if (protectedCount > 0)
                    "${apps.size} apps found (${protectedCount} protected)"
                else
                    "${apps.size} apps found in last 24h"
                btnKillAll.text = "Kill All ($killableCount)"
            }
        }.start()
    }

    private fun updateButtonLabels(killableSelectedCount: Int) {
        btnKillSelected.text = "Kill Selected ($killableSelectedCount)"
        val protectCount = adapter.getCheckedUnprotectedPackages().size
        val unprotectCount = adapter.getCheckedProtectedPackages().size
        btnProtect.text = "Protect ($protectCount)"
        btnProtect.isEnabled = protectCount > 0
        btnUnprotect.text = "Unprotect ($unprotectCount)"
        btnUnprotect.isEnabled = unprotectCount > 0
    }

    private fun startKilling(packages: List<String>) {
        if (!ForceStopAccessibilityService.isEnabled(this)) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }
        val safe = packages.filter { it !in whitelist }
        if (safe.isEmpty()) return
        statusText.text = "Killing ${safe.size} app(s)..."
        ForceStopAccessibilityService.startKilling(safe)
    }
}
