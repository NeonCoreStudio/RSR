package com.example.clashroyalelogger

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.clashroyalelogger.di.RepositoryProvider

class DataManagementActivity : AppCompatActivity() {
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var sessionAdapter: SessionAdapter
    private lateinit var tvSessionCount: TextView
    private lateinit var tvTotalSize: TextView
    private lateinit var btnExportAll: Button
    private lateinit var btnDeleteAll: Button
    private lateinit var emptyStateLayout: LinearLayout
    
    private var sessions: List<SessionData> = emptyList()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_data_management)
        
        initViews()
        setupRecyclerView()
        loadSessions()
    }
    
    private fun initViews() {
        recyclerView = findViewById(R.id.rvSessions)
        tvSessionCount = findViewById(R.id.tvSessionCount)
        tvTotalSize = findViewById(R.id.tvTotalSize)
        btnExportAll = findViewById(R.id.btnExportAll)
        btnDeleteAll = findViewById(R.id.btnDeleteAll)
        emptyStateLayout = findViewById(R.id.layoutEmptyState)
        
        btnExportAll.setOnClickListener { exportAllSessions() }
        btnDeleteAll.setOnClickListener { deleteAllSessions() }
    }
    
    private fun setupRecyclerView() {
        sessionAdapter = SessionAdapter(
            sessions = sessions,
            onExportClick = { session -> exportSession(session) },
            onDeleteClick = { session -> deleteSession(session) },
            onViewClick = { session -> viewSessionDetails(session) },
            onVideoClick = { session -> openSessionViewer(session) }
        )
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = sessionAdapter
    }
    
    private fun loadSessions() {
        sessions = SessionDataManager.getAllSessions()
        sessionAdapter.updateSessions(sessions)
        updateUI()
    }
    
    private fun updateUI() {
        if (sessions.isEmpty()) {
            recyclerView.visibility = RecyclerView.GONE
            emptyStateLayout.visibility = LinearLayout.VISIBLE
            btnExportAll.isEnabled = false
            btnDeleteAll.isEnabled = false
        } else {
            recyclerView.visibility = RecyclerView.VISIBLE
            emptyStateLayout.visibility = LinearLayout.GONE
            btnExportAll.isEnabled = true
            btnDeleteAll.isEnabled = true
        }
        
        tvSessionCount.text = "${sessions.size} sessions found"
        
        val totalSizeMB = sessions.sumOf { session ->
            var size = 0L
            session.csvFile?.let { if (it.exists()) size += it.length() }
            session.metadataFile?.let { if (it.exists()) size += it.length() }
            session.videoFile?.let { if (it.exists()) size += it.length() }
            size
        } / (1024.0 * 1024.0)
        
        tvTotalSize.text = "Total: %.2f MB".format(totalSizeMB)
    }
    
    private fun exportSession(session: SessionData) {
        if (!session.hasAllFiles) {
            Toast.makeText(this, "Cannot export incomplete session", Toast.LENGTH_SHORT).show()
            return
        }
        
        SessionDataManager.exportSessionAsZip(this, session) { success, message ->
            runOnUiThread {
                Toast.makeText(this@DataManagementActivity, message, Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun exportAllSessions() {
        val completeSessions = sessions.filter { it.hasAllFiles }
        if (completeSessions.isEmpty()) {
            Toast.makeText(this, "No complete sessions to export", Toast.LENGTH_SHORT).show()
            return
        }
        
        SessionDataManager.exportAllSessions(this) { success, message ->
            runOnUiThread {
                Toast.makeText(this@DataManagementActivity, message, Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun deleteSession(session: SessionData) {
        AlertDialog.Builder(this)
            .setTitle("Delete Session")
            .setMessage("Are you sure you want to delete this session? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                val success = RepositoryProvider.sessionRepository.deleteSession(session.sessionId)
                if (success) {
                    Toast.makeText(this@DataManagementActivity, "Session deleted", Toast.LENGTH_SHORT).show()
                    loadSessions() // Refresh the list
                } else {
                    Toast.makeText(this@DataManagementActivity, "Failed to delete session", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun deleteAllSessions() {
        if (sessions.isEmpty()) {
            Toast.makeText(this, "No sessions to delete", Toast.LENGTH_SHORT).show()
            return
        }
        
        AlertDialog.Builder(this)
            .setTitle("Delete All Sessions")
            .setMessage("Are you sure you want to delete all ${sessions.size} sessions? This action cannot be undone.")
            .setPositiveButton("Delete All") { _, _ ->
                var deletedCount = 0
                sessions.forEach { session ->
                    if (RepositoryProvider.sessionRepository.deleteSession(session.sessionId)) {
                        deletedCount++
                    }
                }
                Toast.makeText(this@DataManagementActivity, "Deleted $deletedCount sessions", Toast.LENGTH_SHORT).show()
                loadSessions() // Refresh the list
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun viewSessionDetails(session: SessionData) {
        val details = """
            Session ID: ${session.sessionId}
            Date: ${session.formattedDate}
            Duration: ${session.formattedDuration}
            Touch Events: ${session.touchEventCount}
            Size: ${session.sizeInMB}
            Status: ${if (session.isComplete) "Complete" else "Incomplete"}
            
            Files:
            • CSV: ${if (session.csvFile?.exists() == true) "✓" else "✗"}
            • Metadata: ${if (session.metadataFile?.exists() == true) "✓" else "✗"}
            • Video: ${if (session.videoFile?.exists() == true) "✓" else "✗"}
        """.trimIndent()
        
        AlertDialog.Builder(this)
            .setTitle("Session Details")
            .setMessage(details)
            .setPositiveButton("OK", null)
            .setNeutralButton("Open Viewer") { _, _ ->
                openSessionViewer(session)
            }
            .show()
    }
    
    private fun openSessionViewer(session: SessionData) {
        if (session.videoFile?.exists() != true) {
            Toast.makeText(this, "Video file not found", Toast.LENGTH_SHORT).show()
            return
        }
        
        SessionViewerActivity.start(this, session.sessionId)
    }
}