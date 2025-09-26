package com.schotech.videoapp.ui.form

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.schotech.videoapp.Fragments.UserViewModel
import com.schotech.videoapp.R
import com.schotech.videoapp.databinding.FragmentFormBinding
import com.schotech.videoapp.ui.PhoneCalls.CallLogsActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class FormFragment : Fragment() {

    private val userViewModel: UserViewModel by activityViewModels()
    private lateinit var binding: FragmentFormBinding
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.all { it.value }) {
                findNavController().navigate(R.id.action_form_to_video)
            } else {
                Toast.makeText(requireContext(), "Storage permissions required", Toast.LENGTH_SHORT)
                    .show()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentFormBinding.inflate(inflater, container, false)
        binding.btnNext.setOnClickListener {
            val nameInput = binding.etName.text.toString().trim()
            val name = nameInput.split(" ")
                .joinToString(" ") { word ->
                    word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                }
            val age = "2"
            val insertTime = age.toFloatOrNull() ?: 0f
            if (name.isNotEmpty() && age.isNotEmpty()) {
                userViewModel.setUserData(name, age, insertTime)
                if (checkStoragePermissions()) {
                    findNavController().navigate(R.id.action_form_to_video)
                } else {
                    requestStoragePermissions()
                }
            } else {
                Toast.makeText(
                    requireContext(),
                    "Please enter name, age, and insert time",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        binding.btnPhoneCalls.setOnClickListener {
            val intent = Intent(requireContext(), CallLogsActivity::class.java)
            startActivity(intent)
        }

        return binding.root
    }

    private fun checkStoragePermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.os.Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent =
                    android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = android.net.Uri.parse("package:${requireContext().packageName}")
                startActivity(intent)
            } catch (e: Exception) {
                val intent =
                    android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivity(intent)
            }
        } else {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }
}