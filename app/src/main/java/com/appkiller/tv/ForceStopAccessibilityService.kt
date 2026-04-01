package com.appkiller.tv

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo

class ForceStopAccessibilityService : AccessibilityService() {

    private enum class State {
        IDLE, WAITING_FOR_FORCE_STOP_BTN, WAITING_FOR_CONFIRM_BTN
    }

    private var state = State.IDLE
    private val queue = ArrayDeque<String>()
    private var currentPackage: String? = null

    private val skipFallbackRunnable = Runnable {
        if (state == State.WAITING_FOR_FORCE_STOP_BTN) {
            Log.d(TAG, "Fallback timeout — Force Stop not found, skipping $currentPackage")
            state = State.IDLE
            processNextPackage()
        }
    }
    private val handler = Handler(Looper.getMainLooper())
    private var eventsSinceStateChange = 0

    private val processRunnable = Runnable { processCurrentState() }

    companion object {
        private const val TAG = "AppKiller"
        private var instance: ForceStopAccessibilityService? = null

        fun isEnabled(context: Context): Boolean {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val services = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            return services.any { it.id.contains(context.packageName) }
        }

        fun startKilling(packages: List<String>) {
            val svc = instance ?: run {
                Log.e(TAG, "startKilling: service instance is null")
                return
            }
            svc.queue.clear()
            svc.queue.addAll(packages)
            svc.handler.post { svc.processNextPackage() }
        }
    }

    override fun onServiceConnected() {
        instance = this
        Log.d(TAG, "Accessibility service connected")
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || state == State.IDLE) return

        eventsSinceStateChange++

        if (eventsSinceStateChange > 40) {
            Log.w(TAG, "Timeout waiting in state $state — skipping package")
            eventsSinceStateChange = 0
            state = State.IDLE
            handler.removeCallbacks(processRunnable)
            handler.postDelayed(::processNextPackage, 500)
            return
        }

        handler.removeCallbacks(processRunnable)
        handler.postDelayed(processRunnable, 300)
    }

    private fun processCurrentState() {
        when (state) {
            State.WAITING_FOR_FORCE_STOP_BTN -> clickForceStop()
            State.WAITING_FOR_CONFIRM_BTN -> clickConfirm()
            State.IDLE -> {}
        }
    }

    private fun processNextPackage() {
        if (queue.isEmpty()) {
            state = State.IDLE
            returnToMainApp()
            return
        }
        val pkg = queue.removeFirst()
        currentPackage = pkg
        Log.d(TAG, "Processing package: $pkg")
        eventsSinceStateChange = 0
        state = State.WAITING_FOR_FORCE_STOP_BTN

        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", pkg, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)

        // Fallback: if Force Stop button never appears, skip after 3 seconds
        handler.removeCallbacks(skipFallbackRunnable)
        handler.postDelayed(skipFallbackRunnable, 3000)
    }

    private fun clickForceStop() {
        val root = rootInActiveWindow ?: run {
            Log.d(TAG, "clickForceStop: rootInActiveWindow is null")
            return
        }

        Log.d(TAG, "clickForceStop: scanning window '${root.packageName}'")
        dumpNodes(root, 0)

        val texts = listOf("Force stop", "Force Stop", "FORCE STOP", "Forzar detención")
        for (text in texts) {
            val nodes = root.findAccessibilityNodeInfosByText(text)
            Log.d(TAG, "  search '$text': found ${nodes.size} nodes")
            for (node in nodes) {
                Log.d(TAG, "    node: class=${node.className} clickable=${node.isClickable} enabled=${node.isEnabled} text='${node.text}'")
                val clickable = findClickableNode(node)
                if (clickable != null) {
                    if (clickable.isEnabled) {
                        Log.d(TAG, "  clicking Force Stop (via ${clickable.className})")
                        handler.removeCallbacks(skipFallbackRunnable)
                        clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        clickable.recycle()
                        node.recycle()
                        root.recycle()
                        eventsSinceStateChange = 0
                        state = State.WAITING_FOR_CONFIRM_BTN
                        return
                    } else {
                        Log.d(TAG, "  Force Stop found but disabled — app not running, skipping")
                        handler.removeCallbacks(skipFallbackRunnable)
                        clickable.recycle()
                        node.recycle()
                        root.recycle()
                        state = State.IDLE
                        handler.postDelayed(::processNextPackage, 300)
                        return
                    }
                }
            }
        }

        root.recycle()
        Log.d(TAG, "clickForceStop: button not found yet, waiting for more events ($eventsSinceStateChange/40)")
    }

    private fun clickConfirm() {
        val root = rootInActiveWindow ?: return

        val hasCancelButton = root.findAccessibilityNodeInfosByText("Cancel").isNotEmpty()
                || root.findAccessibilityNodeInfosByText("Cancelar").isNotEmpty()
        Log.d(TAG, "clickConfirm: hasCancelButton=$hasCancelButton")

        if (!hasCancelButton) {
            root.recycle()
            return
        }

        val confirmTexts = listOf("Force stop", "Force Stop", "OK", "Aceptar")
        for (text in confirmTexts) {
            val nodes = root.findAccessibilityNodeInfosByText(text)
            for (node in nodes) {
                val clickable = findClickableNode(node)
                if (clickable != null && clickable.isEnabled) {
                    Log.d(TAG, "  clicking confirm button '$text'")
                    clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    clickable.recycle()
                    node.recycle()
                    root.recycle()
                    currentPackage?.let { RunningAppsHelper.killedAt[it] = System.currentTimeMillis() }
                    state = State.IDLE
                    handler.postDelayed(::processNextPackage, 800)
                    return
                }
            }
        }

        root.recycle()
    }

    // Returns the node itself if clickable, otherwise walks up to find a clickable ancestor
    private fun findClickableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isClickable) return node
        var parent = node.parent
        var depth = 0
        while (parent != null && depth < 5) {
            if (parent.isClickable) return parent
            val next = parent.parent
            parent = next
            depth++
        }
        return null
    }

    // Dumps the top 2 levels of the accessibility tree so we can see button texts
    private fun dumpNodes(node: AccessibilityNodeInfo, depth: Int) {
        if (depth > 2) return
        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""
        if (text.isNotEmpty() || desc.isNotEmpty()) {
            Log.d(TAG, "  ${"  ".repeat(depth)}[${node.className}] text='$text' desc='$desc' clickable=${node.isClickable} enabled=${node.isEnabled}")
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { dumpNodes(it, depth + 1) }
        }
    }

    private fun returnToMainApp() {
        val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        intent?.let { startActivity(it) }
    }

    override fun onInterrupt() {}
}
