package com.schotech.videoapp.Fragments

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class UserViewModel @Inject constructor() : ViewModel() {

    private val _userData = MutableLiveData<Triple<String, String, Float>>()
    val userData: LiveData<Triple<String, String, Float>> = _userData

    fun setUserData(name: String, age: String, insertTime: Float) {
        _userData.value = Triple(name, age, insertTime)
    }
}