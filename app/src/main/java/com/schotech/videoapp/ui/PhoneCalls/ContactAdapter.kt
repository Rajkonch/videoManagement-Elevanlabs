package com.schotech.videoapp.ui.PhoneCalls

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.schotech.videoapp.R
import com.schotech.videoapp.data.Contact
import com.schotech.videoapp.data.model.CallHistory
import com.schotech.videoapp.databinding.ItemContactBinding
import java.text.SimpleDateFormat
import java.util.*

class ContactAdapter(
    private val activity: Activity,
    private val onClick: (Contact) -> Unit
) : RecyclerView.Adapter<ContactAdapter.ContactViewHolder>() {
    private var list = listOf<Contact>()
    companion object {
        const val REQUEST_CODE_CALL = 101
    }
    fun submitList(newList: List<Contact>) {
        list = newList
        notifyDataSetChanged()
    }
    inner class ContactViewHolder(val binding: ItemContactBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val binding = ItemContactBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ContactViewHolder(binding)
    }

    override fun getItemCount(): Int = list.size

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        val contact = list[position]
        holder.binding.contactName.text = if (contact.name.isBlank()) "No Name" else contact.name
        holder.binding.contactNumber.text = contact.number
        holder.binding.callType.text = contact.type

        val callTypeColor = when (contact.type) {
            "Received" -> R.color.call_received
            "Dialed" -> R.color.call_dialed
            "Missed" -> R.color.call_missed
            "Rejected" -> R.color.call_rejected
            else -> R.color.black
        }
        holder.binding.callType.setTextColor(ContextCompat.getColor(activity, callTypeColor))

        if (contact.isFromCallLog && contact.callCount > 1) {
            holder.binding.callCountView.text = "${contact.callCount}+ calls"
            holder.binding.callCountView.visibility = View.VISIBLE
        } else {
            holder.binding.callCountView.visibility = View.GONE
        }

        val minutes = contact.duration / 60
        val seconds = contact.duration % 60
        holder.binding.duration.text = if (minutes > 0) "$minutes min $seconds sec" else "$seconds sec"
        holder.binding.start.text = contact.date

        holder.binding.root.setOnClickListener {
            showCallHistoryBottomSheet(contact.number)
        }

        holder.binding.btnCallRecord.setOnClickListener {
            makeDirectCall(contact.number)
        }
    }
    private fun makeDirectCall(phoneNumber: String) {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CALL_PHONE)
            == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                val intent = Intent(Intent.ACTION_CALL).apply {
                    data = Uri.parse("tel:$phoneNumber")
                }
                activity.startActivity(intent)
                println("Calling number: $phoneNumber") // debug log
            } catch (e: Exception) {
                Toast.makeText(activity, "Call failed: ${e.message}", Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            }
        } else {

            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.CALL_PHONE),
                REQUEST_CODE_CALL
            )
            Toast.makeText(activity, "Please grant CALL_PHONE permission", Toast.LENGTH_SHORT).show()
        }
    }
    private fun showCallHistoryBottomSheet(phoneNumber: String) {
        val dialog = BottomSheetDialog(activity)
        val view = LayoutInflater.from(activity).inflate(R.layout.bottom_call_history, null)
        val recyclerView = view.findViewById<RecyclerView>(R.id.historyRecyclerView)
        val callHistory = getCallLogs(phoneNumber)
        recyclerView.layoutManager = LinearLayoutManager(activity)
        recyclerView.adapter = CallHistoryAdapter(callHistory)
        dialog.setContentView(view)
        dialog.show()
    }
    private fun getCallLogs(number: String): List<CallHistory> {
        val list = mutableListOf<CallHistory>()
        val cursor = activity.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            null,
            CallLog.Calls.NUMBER + "=?",
            arrayOf(number),
            CallLog.Calls.DATE + " DESC"
        )

        cursor?.use {
            val nameIndex = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
            val numberIndex = it.getColumnIndex(CallLog.Calls.NUMBER)
            val durationIndex = it.getColumnIndex(CallLog.Calls.DURATION)
            val typeIndex = it.getColumnIndex(CallLog.Calls.TYPE)
            val dateIndex = it.getColumnIndex(CallLog.Calls.DATE)

            while (it.moveToNext()) {
                val name = it.getString(nameIndex) ?: "No Name"
                val phone = it.getString(numberIndex)
                val duration = it.getInt(durationIndex)
                val typeInt = it.getInt(typeIndex)
                val dateLong = it.getLong(dateIndex)

                val formatter = SimpleDateFormat("dd MMM yyyy hh:mm a", Locale.getDefault())
                val dateString = formatter.format(Date(dateLong))

                val durationStr = if (duration >= 60) "${duration / 60} min ${duration % 60} sec" else "$duration sec"

                val type = when (typeInt) {
                    CallLog.Calls.INCOMING_TYPE -> "Incoming"
                    CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
                    CallLog.Calls.MISSED_TYPE -> "Missed"
                    CallLog.Calls.REJECTED_TYPE -> "Rejected"
                    CallLog.Calls.BLOCKED_TYPE -> "Blocked"
                    else -> "Unknown"
                }

                list.add(CallHistory(phone, name, durationStr, type, dateString))
            }
        }

        return list
    }
}
