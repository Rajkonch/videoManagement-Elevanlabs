package com.schotech.videoapp.ui.PhoneCalls

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.schotech.videoapp.data.model.CallHistory
import com.schotech.videoapp.databinding.ItemCallHistoryBinding
import kotlin.text.isBlank

class CallHistoryAdapter(private val list: List<CallHistory>) :
    RecyclerView.Adapter<CallHistoryAdapter.HistoryViewHolder>() {

    inner class HistoryViewHolder(val binding: ItemCallHistoryBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding =
            ItemCallHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HistoryViewHolder(binding)
    }

    override fun getItemCount(): Int = list.size

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val history = list[position]
        holder.binding.name.text = if (history.name.isBlank()) "No Name" else history.name
        holder.binding.number.text = history.number
        holder.binding.type.text = history.type
        holder.binding.date.text = history.date
        holder.binding.duration.text = history.duration
    }
}
