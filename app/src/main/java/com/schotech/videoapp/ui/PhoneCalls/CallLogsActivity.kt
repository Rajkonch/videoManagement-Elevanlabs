package com.schotech.videoapp.ui.PhoneCalls

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.schotech.videoapp.R
import com.schotech.videoapp.databinding.ActivityCallLogsBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlin.getValue

@AndroidEntryPoint
class CallLogsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCallLogsBinding
    private val viewModel: CallMainViewModel by viewModels()
    private lateinit var adapter: ContactAdapter
    private val REQUEST_CODE_PERMISSIONS = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCallLogsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        viewModel.loadContacts()
        checkPermissions()
        setupUI()
        observeViewModel()

    }
    private fun observeViewModel() {
        viewModel.filteredContacts.observe(this) {
            adapter.submitList(it)
        }
        viewModel.callRequest.observe(this) { phoneNumber ->
            if (phoneNumber.isNotEmpty()) {
                val callIntent = Intent(Intent.ACTION_CALL).apply {
                    data = Uri.parse("tel:$phoneNumber")
                }
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    startActivity(callIntent)
                } else {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.CALL_PHONE),
                        REQUEST_CODE_PERMISSIONS
                    )
                }
            }
        }

        viewModel.contacts.observe(this) { contactList ->
            showLoading(false)
            if (contactList.isNullOrEmpty()) {
                Toast.makeText(this, "No Data Found", Toast.LENGTH_SHORT).show()
            } else {
                adapter.submitList(contactList)
            }
        }

        /*  viewModel.todayCounts.observe(this) { counts ->
              binding.filterAll.text = "All (${counts["All"] ?: 0})"
              binding.filterDialed.text = "Dialed (${counts["Dialed"] ?: 0})"
              binding.filterReceived.text = "Received (${counts["Received"] ?: 0})"
              binding.filterMissed.text = "Missed (${counts["Missed"] ?: 0})"
              binding.filterRejected.text = "Rejected (${counts["Rejected"] ?: 0})"
          }*/
    }
    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }
    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.CALL_PHONE
        )
        ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE_PERMISSIONS)
    }
    private fun setupUI() {
        adapter =  ContactAdapter(this) { contact ->
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        setupSearch()
        setupDialPad()
        setupFilters()

        binding.btnToggleDialPad.setOnClickListener {
            binding.dialContainer.visibility =
                if (binding.dialContainer.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
    }
    private fun setupDialPad() {
        val digits = listOf(
            binding.btn0, binding.btn1, binding.btn2, binding.btn3,
            binding.btn4, binding.btn5, binding.btn6,
            binding.btn7, binding.btn8, binding.btn9,
            binding.btnStar, binding.btnHash
        )

        digits.forEach { btn ->
            btn.setOnClickListener { binding.typedNumber.append(btn.text) }
        }

        binding.btnDelete.setOnClickListener {
            val current = binding.typedNumber.text.toString()
            if (current.isNotEmpty()) {
                binding.typedNumber.setText(current.dropLast(1))
            }
        }

        binding.btnCall.setOnClickListener {
            val number = binding.typedNumber.text.toString()
            if (number.isNotEmpty()) {
                makeDirectCall(number)
            } else {
                Toast.makeText(this, "Enter a number", Toast.LENGTH_SHORT).show()
            }
        }
    }
    private fun makeDirectCall(number: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                val intent = Intent(Intent.ACTION_CALL).apply {
                    data = Uri.parse("tel:$number")
                }
                startActivity(intent)
                println("Calling number: $number")
            } catch (e: Exception) {
                Toast.makeText(this, "Call failed: ${e.message}", Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            }
        } else {
            // Request permission
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CALL_PHONE),
                REQUEST_CODE_PERMISSIONS
            )
            println("Requesting CALL_PHONE permission")
        }
    }
    private fun setupFilters() {
        val filters = listOf(
            binding.filterAll to "All",
            binding.filterDialed to "Dialed",
            binding.filterReceived to "Received",
            binding.filterMissed to "Missed",
            binding.filterRejected to "Rejected"
        )

        filters.forEach { (view, type) ->
            (view as TextView).setOnClickListener {
                filters.forEach { (v, _) ->
                    (v as TextView).apply {
                        setBackgroundResource(R.drawable.filter_unselected)
                        setTextColor(ContextCompat.getColor(this@CallLogsActivity, R.color.black))
                    }
                }

                view.apply {
                    setBackgroundResource(R.drawable.filter_selected)
                    setTextColor(ContextCompat.getColor(this@CallLogsActivity, R.color.white))
                }

                viewModel.applyFilters(type, binding.Searchdata.text.toString())
            }
        }

        binding.filterAll.performClick()
    }
    private fun setupSearch() {
        val watcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.applyFilters(getSelectedFilter(), s.toString())
            }
        }

        binding.Searchdata.addTextChangedListener(watcher)
        binding.typedNumber.addTextChangedListener(watcher)
    }
    private fun getSelectedFilter(): String {
        return when {
            binding.filterDialed.isSelected -> "Dialed"
            binding.filterReceived.isSelected -> "Received"
            binding.filterMissed.isSelected -> "Missed"
            binding.filterRejected.isSelected -> "Rejected"
            else -> "All"
        }
    }
    override fun onResume() {
        super.onResume()
        showLoading(true)
        viewModel.loadContacts()
        binding.typedNumber.requestFocus()

    }
}
