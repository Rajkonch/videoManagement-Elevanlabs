package com.schotech.videoapp.ui.PhoneCalls

import android.app.Application
import android.content.ContentResolver
import android.provider.CallLog
import android.provider.ContactsContract
import androidx.lifecycle.*
import com.schotech.videoapp.data.Contact
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import kotlin.collections.filter
import kotlin.collections.forEach
import kotlin.collections.map
import kotlin.collections.set
import kotlin.collections.sortedByDescending
import kotlin.io.use
import kotlin.text.contains
import kotlin.text.removePrefix
import kotlin.text.replace
import kotlin.to

@HiltViewModel
class CallMainViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {
    private val contentResolver: ContentResolver = application.contentResolver
    private val _contacts = MutableLiveData<List<Contact>>()
    val contacts: LiveData<List<Contact>> = _contacts
    private val _filteredContacts = MutableLiveData<List<Contact>>()
    val filteredContacts: LiveData<List<Contact>> = _filteredContacts
    private val _callRequest = MutableLiveData<String>()
    val callRequest: LiveData<String> = _callRequest
    private val _todayCounts = MutableLiveData<Map<String, Int>>()
    val todayCounts: LiveData<Map<String, Int>> = _todayCounts
    private var fullList: List<Contact> = emptyList()
    fun loadContacts() {
        viewModelScope.launch(Dispatchers.IO) {
            val contactMap = LinkedHashMap<String, Contact>()

            val projection = arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION
            )

            val cursor = contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )

            // --- Today ka start time (midnight se ab tak) ---
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val startOfDay = calendar.timeInMillis

            val todayCounts = mutableMapOf(
                "All" to 0,
                "Dialed" to 0,
                "Received" to 0,
                "Missed" to 0,
                "Rejected" to 0
            )

            cursor?.use {
                val nameIdx = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val numberIdx = it.getColumnIndex(CallLog.Calls.NUMBER)
                val typeIdx = it.getColumnIndex(CallLog.Calls.TYPE)
                val dateIdx = it.getColumnIndex(CallLog.Calls.DATE)
                val durationIdx = it.getColumnIndex(CallLog.Calls.DURATION)

                while (it.moveToNext()) {
                    val name = it.getString(nameIdx) ?: "Unknown"
                    val number = it.getString(numberIdx)
                        ?.replace(" ", "")        // spaces remove
                        ?.removePrefix("+91")     // +91 remove
                        ?: continue
                    val typeCode = it.getInt(typeIdx)
                    val dateMillis = it.getLong(dateIdx)
                    val duration = it.getLong(durationIdx)

                    val type = when (typeCode) {
                        CallLog.Calls.OUTGOING_TYPE -> "Dialed"
                        CallLog.Calls.INCOMING_TYPE -> "Received"
                        CallLog.Calls.MISSED_TYPE -> "Missed"
                        CallLog.Calls.REJECTED_TYPE -> "Rejected"
                        else -> "Other"
                    }

                    // --- Count only today's calls ---
                    if (dateMillis >= startOfDay) {
                        todayCounts["All"] =  (todayCounts["All"] ?: 0) + 1
                        todayCounts[type] =  (todayCounts[type] ?: 0) + 1
                    }

                    if (contactMap.containsKey(number)) {
                        contactMap[number]?.callCount = (contactMap[number]?.callCount ?: 0) + 1
                    } else {
                        contactMap[number] =  Contact(
                            name = name,
                            number = number,
                            type = type,
                            date = "", // Format later
                            dateMillis = dateMillis,
                            duration = duration,
                            isFromCallLog = true,
                            callCount = 1,
                            filePath = null
                        )
                    }
                }
            }

            // Load phone contacts also
            val phoneContacts = loadPhoneContacts()
            phoneContacts.forEach { contact ->
                val normalized = contact.number.replace(" ", "")
                if (!contactMap.containsKey(normalized)) {
                    contactMap[normalized] = contact
                }
            }

            fullList = contactMap.values
                .sortedByDescending { it.dateMillis }
                .map {
                    it.copy(date = SimpleDateFormat("dd-MM-yyyy hh:mm a", Locale.getDefault()).format(Date(it.dateMillis)))
                }

            _contacts.postValue(fullList)
            _filteredContacts.postValue(fullList)

            // --- Post today counts for UI ---
            _todayCounts.postValue(todayCounts)
        }
    }


    private fun loadPhoneContacts(): List<Contact> {
        val contactList = mutableListOf<Contact>()

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        val cursor = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            null
        )

        cursor?.use {
            val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

            while (it.moveToNext()) {
                val name = it.getString(nameIdx) ?: "Unknown"
                val number = it.getString(numberIdx) ?: continue
                contactList.add(Contact(name, number.replace(" ", ""), isFromCallLog = false))
            }
        }

        return contactList
    }

    fun applyFilters(type: String, query: String) {
        viewModelScope.launch(Dispatchers.Default) {
            val filtered = fullList.filter {
                (type == "All" || it.type == type) &&
                        (it.name.contains(query, true) || it.number.contains(query))
            }
            _filteredContacts.postValue(filtered)
        }
    }

    fun makeCall(number: String) {
        _callRequest.postValue(number)
    }
}
