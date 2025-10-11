package com.example.clashroyalelogger

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SessionAdapter(
    private var sessions: List<SessionData>,
    private val onExportClick: (SessionData) -> Unit,
    private val onDeleteClick: (SessionData) -> Unit,
    private val onViewClick: (SessionData) -> Unit,
    private val onVideoClick: (SessionData) -> Unit
) : RecyclerView.Adapter<SessionAdapter.SessionViewHolder>() {

    class SessionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvSessionDate: TextView = itemView.findViewById(R.id.tvSessionDate)
        val tvSessionInfo: TextView = itemView.findViewById(R.id.tvSessionInfo)
        val tvSessionStats: TextView = itemView.findViewById(R.id.tvSessionStats)
        val tvSessionStatus: TextView = itemView.findViewById(R.id.tvSessionStatus)
        val btnExport: Button = itemView.findViewById(R.id.btnExport)
        val btnDelete: Button = itemView.findViewById(R.id.btnDelete)
        val btnView: Button = itemView.findViewById(R.id.btnView)
        val btnVideo: Button = itemView.findViewById(R.id.btnVideo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_session, parent, false)
        return SessionViewHolder(view)
    }

    override fun onBindViewHolder(holder: SessionViewHolder, position: Int) {
        val session = sessions[position]
        
        holder.tvSessionDate.text = session.formattedDate
        holder.tvSessionInfo.text = "Session: ${session.sessionId}"
        holder.tvSessionStats.text = "${session.touchEventCount} events • ${session.formattedDuration} • ${session.sizeInMB}"
        
        // Set status with color coding
        if (session.isComplete) {
            holder.tvSessionStatus.text = "✓ Complete"
            holder.tvSessionStatus.setTextColor(holder.itemView.context.getColor(android.R.color.holo_green_dark))
        } else {
            holder.tvSessionStatus.text = "⚠ Incomplete"
            holder.tvSessionStatus.setTextColor(holder.itemView.context.getColor(android.R.color.holo_orange_dark))
        }
        
        // Set button click listeners
        holder.btnExport.setOnClickListener { onExportClick(session) }
        holder.btnDelete.setOnClickListener { onDeleteClick(session) }
        holder.btnView.setOnClickListener { onViewClick(session) }
        holder.btnVideo.setOnClickListener { onVideoClick(session) }
        
        // Enable/disable export button based on completeness
        holder.btnExport.isEnabled = session.hasAllFiles
        holder.btnExport.alpha = if (session.hasAllFiles) 1.0f else 0.5f
        
        // Enable/disable video button based on video and CSV file availability
        val hasVideoAndCsv = session.videoFile?.exists() == true && session.csvFile?.exists() == true
        holder.btnVideo.isEnabled = hasVideoAndCsv
        holder.btnVideo.alpha = if (hasVideoAndCsv) 1.0f else 0.5f
    }

    override fun getItemCount(): Int = sessions.size

    fun updateSessions(newSessions: List<SessionData>) {
        sessions = newSessions
        notifyDataSetChanged()
    }
}