package com.schotech.videoapp.ui.video

import android.Manifest
import android.app.ProgressDialog
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.schotech.videoapp.Fragments.UserViewModel
import com.schotech.videoapp.R
import com.schotech.videoapp.databinding.FragmentVideoBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

@AndroidEntryPoint
class VideoFragment : Fragment() {
    private val userViewModel: UserViewModel by activityViewModels()
    private var _binding: FragmentVideoBinding? = null
    private val binding get() = _binding!!
    private lateinit var exoPlayer: ExoPlayer
    private var progressDialog: ProgressDialog? = null

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.all { it.value }) {
                processVideo()
            } else {
                showToast("Storage permissions required")
            }
        }

    private val ELEVENLABS_API_KEY =
        "sk_ddd24f9c3c70c98727d866409c8a9fe5e80fc4975065e850"
    private val ELEVENLABS_VOICE_ID = "VSTMZcXyuCkRRK5uSZBZ"
    private val TAG = "VideoFragmentDebug"
    private val FFMPEG_TAG = "FFmpegDebug"

    companion object {
        private const val TTS_OUTPUT_FORMAT = "mp3_44100_128"
        private const val AUDIO_BITRATE = "192k"
        private const val PAD_DURATION = "1"
        private const val MAX_API_RETRIES = 3
        private const val MAX_FFMPEG_RETRIES = 2
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVideoBinding.inflate(inflater, container, false)
        exoPlayer = ExoPlayer.Builder(requireContext()).build()
        binding.playerView.player = exoPlayer

        Log.d(TAG, "onCreateView called")

        if (checkStoragePermissions()) {
            processVideo()
        } else {
            requestStoragePermissions()
        }

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        exoPlayer.release()
        progressDialog?.dismiss()
        _binding = null
    }

    private fun checkStoragePermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
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
                val intent = android.content.Intent(
                    android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
                ).apply {
                    data = Uri.parse("package:${requireContext().packageName}")
                }
                startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch manage storage intent: ${e.message}")
                startActivity(
                    android.content.Intent(
                        android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                    )
                )
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

    private fun processVideo() {
        lifecycleScope.launch {
            progressDialog = ProgressDialog(requireContext()).apply {
                setMessage("Processing video...")
                setCancelable(false)
                show()
            }
            binding.progressBar.visibility = View.VISIBLE
            binding.playerView.visibility = View.GONE
            Log.d(TAG, "processVideo started")

            val videoFile = copyRawResourceToStorage(R.raw.sample_videos, "sample_videos.mp4")
            if (videoFile == null || !videoFile.exists()) {
                Log.e(TAG, "Video file copy failed or file not found, aborting process")
                showToast("Failed to copy video")
                finishProcessing()
                return@launch
            }

            val ttsAudio = File(
                requireContext().getExternalFilesDir(Environment.DIRECTORY_MUSIC),
                "tts_audio.mp3"
            )
            val outputVideo = File(
                requireContext().getExternalFilesDir(Environment.DIRECTORY_MOVIES),
                "final_video.mp4"
            )

            val user = userViewModel.userData.value
            if (user == null) {
                showToast("User data not ready")
                finishProcessing()
                return@launch
            }

            val name = user.first
            Log.d(TAG, "User name: $name")

            try {
                // 1) Generate TTS audio
                generateTTSAudioFromElevenLabs(name, ttsAudio)
                if (!ttsAudio.exists() || ttsAudio.length() == 0L) {
                    showToast("TTS audio file is invalid")
                    finishProcessing()
                    return@launch
                }

                // 2) Calculate smart insert time for existing 2-3s gap
                val gapStart = 2f
                val gapEnd = 3f
                val ttsDuration = getAudioDuration(ttsAudio)
                val gapSize = gapEnd - gapStart

                /*val insertTime = if (ttsDuration <= gapSize) {
                    // Center align inside existing gap
                    gapStart + ((gapSize - ttsDuration) / 2f)
                } else {
                    // If longer, start from gapStart (will cover gap and mute beyond if needed)
                    gapStart
                }*/

                val adjustedGapStart = when {
                    name.length <= 8 -> gapStart - 0.03f   // reduce by 20ms
                    name.length >= 15 -> gapStart + 0.08f     // increase by 1s
                    else -> gapStart
                }

                val insertTime = if (ttsDuration <= gapSize) {
                    adjustedGapStart + ((gapSize - ttsDuration) / 2f)
                } else {
                    adjustedGapStart
                }


                Log.d(TAG, "TTS duration=$ttsDuration sec, insertTime=$insertTime sec (gap $gapStart-$gapEnd)")

                // 3) Validate + Merge
                validateAudioCompatibility(videoFile, ttsAudio)
                mergeTwoAudioTracks(videoFile, ttsAudio, outputVideo, insertTime, ttsDuration)

                // 4) Play final output
                binding.tvOverlay.text = name
                binding.playerView.visibility = View.VISIBLE
                playVideo(outputVideo)

            } catch (e: Exception) {
                Log.e(TAG, "Error during video processing: ${e.message}", e)
                showToast("Processing error: ${e.message}")
            } finally {
                finishProcessing()
                cleanupTempFiles(videoFile, ttsAudio)
            }
        }
    }

    // ---- Add this helper function ----
    private fun getAudioDuration(file: File): Float {
        return try {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(file.absolutePath)
                val durationMs =
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                (durationMs ?: 0L) / 1000f
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting audio duration: ${e.message}")
            0f
        }
    }

    private suspend fun copyRawResourceToStorage(resId: Int, fileName: String): File? =
        withContext(Dispatchers.IO) {
            val file =
                File(requireContext().getExternalFilesDir(Environment.DIRECTORY_MOVIES), fileName)
            try {
                requireContext().resources.openRawResource(resId).use { input ->
                    FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d(TAG, "Raw video copied to: ${file.absolutePath}")
                file
            } catch (e: Exception) {
                Log.e(TAG, "copyRawResource failed: ${e.message}")
                withContext(Dispatchers.Main) {
                    showToast("Failed to copy video: ${e.message}")
                }
                null
            }
        }

    private suspend fun generateTTSAudioFromElevenLabs(text: String, outputFile: File) =
        withContext(Dispatchers.IO) {
            var attempt = 0
            while (attempt < MAX_API_RETRIES) {
                try {
                    Log.d(TAG, "Generating TTS for text: $text (attempt ${attempt + 1})")
                    val client = OkHttpClient()
                    val json = """
                        {
                            "text": "$text",
                            "voice": "$ELEVENLABS_VOICE_ID",
                            "model_id": "eleven_multilingual_v2",
                            "output_format": "$TTS_OUTPUT_FORMAT"
                        }
                    """.trimIndent()

                    val body = RequestBody.create("application/json".toMediaTypeOrNull(), json)
                    val request = Request.Builder()
                        .url("https://api.elevenlabs.io/v1/text-to-speech/$ELEVENLABS_VOICE_ID")
                        .addHeader("xi-api-key", ELEVENLABS_API_KEY)
                        .addHeader("Content-Type", "application/json")
                        .post(body)
                        .build()

                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        response.body?.byteStream()?.use { input ->
                            outputFile.outputStream().use { out ->
                                input.copyTo(out)
                            }
                        }
                        Log.d(
                            TAG,
                            "TTS file created at: ${outputFile.absolutePath}, size: ${outputFile.length()}"
                        )
                        return@withContext
                    } else {
                        Log.e(TAG, "TTS API failed: ${response.code}, ${response.message}")
                        attempt++
                        if (attempt == MAX_API_RETRIES) {
                            withContext(Dispatchers.Main) {
                                showToast("Failed to generate TTS after $MAX_API_RETRIES attempts: ${response.message}")
                            }
                            throw Exception("TTS API failed: ${response.message}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "TTS generation error (attempt ${attempt + 1}): ${e.message}")
                    attempt++
                    if (attempt == MAX_API_RETRIES) {
                        withContext(Dispatchers.Main) {
                            showToast("TTS error after $MAX_API_RETRIES attempts: ${e.message}")
                        }
                        throw e
                    }
                }
            }
        }

    private suspend fun validateAudioCompatibility(videoFile: File, ttsFile: File) =
        withContext(Dispatchers.IO) {
            try {
                val videoAudioInfo = getAudioInfo(videoFile)
                val ttsAudioInfo = getAudioInfo(ttsFile)
                Log.d(TAG, "Video audio info: $videoAudioInfo")
                Log.d(TAG, "TTS audio info: $ttsAudioInfo")

                if (videoAudioInfo.sampleRate != ttsAudioInfo.sampleRate) {
                    Log.w(
                        TAG,
                        "Sample rate mismatch: video=${videoAudioInfo.sampleRate}, tts=${ttsAudioInfo.sampleRate}"
                    )
                    // Optionally, re-encode TTS audio to match video's sample rate
                }
                if (videoAudioInfo.channels != ttsAudioInfo.channels) {
                    Log.w(
                        TAG,
                        "Channel count mismatch: video=${videoAudioInfo.channels}, tts=${ttsAudioInfo.channels}"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to validate audio compatibility: ${e.message}")
            }
        }

    private data class AudioInfo(val sampleRate: String?, val channels: String?)

    private fun getAudioInfo(file: File): AudioInfo {
        return try {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(file.absolutePath)
                val sampleRate =
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
                val channels =
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_NUM_TRACKS)
                AudioInfo(sampleRate, channels)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving audio info for ${file.absolutePath}: ${e.message}")
            AudioInfo(null, null)
        }
    }

    private suspend fun mergeTwoAudioTracks(
        videoFile: File,
        ttsFile: File,
        outputFile: File,
        insertTimeSec: Float,
        ttsDuration: Float
    ) = withContext(Dispatchers.IO) {
        var attempt = 0
        while (attempt < MAX_FFMPEG_RETRIES) {
            try {
                val totalDuration = getVideoDuration(videoFile)
                Log.d(TAG, "Video duration: $totalDuration sec, TTS duration: $ttsDuration sec, insertTime: $insertTimeSec sec")

                val ttsDelayMs = (insertTimeSec * 1000).toInt()
                val muteStart = insertTimeSec
                val muteEnd = muteStart + ttsDuration
                val effectiveMuteEnd = min(muteEnd, totalDuration)

                Log.d(TAG, "Muting original from $muteStart to $effectiveMuteEnd sec for TTS coverage")

                com.arthenica.mobileffmpeg.Config.enableLogCallback { log ->
                    Log.d(FFMPEG_TAG, log.text)
                }

                val filterComplex = if (hasAudio(videoFile)) {
                    // Split original audio into copies for segments
                    val split = "[0:a]asplit=3[ab][am][aa];"
                    val before = "[ab]atrim=0:$muteStart,asetpts=PTS-STARTPTS[before];"
                    val mutePart = "[am]atrim=$muteStart:$effectiveMuteEnd,asetpts=PTS-STARTPTS,volume=0[mute_part];"
                    val after = "[aa]atrim=$effectiveMuteEnd,asetpts=PTS-STARTPTS[after];"
                    val ttsDelayed = "[1:a]adelay=$ttsDelayMs:all=1[a1];"

                    val concatPart = if (effectiveMuteEnd < totalDuration) {
                        "[before][mute_part][after]concat=n=3:v=0:a=1[orig_adjusted];"
                    } else {
                        "[before][mute_part]concat=n=2:v=0:a=1[orig_adjusted];"
                    }

                    val mix = "[orig_adjusted][a1]amix=inputs=2:duration=longest:dropout_transition=0[aout]"

                    split + before + mutePart + after + concatPart + ttsDelayed + mix
                } else {
                    // No original audio, just delayed TTS
                    "[1:a]adelay=$ttsDelayMs:all=1,apad=pad_dur=$PAD_DURATION[aout]"
                }

                val cmd = arrayOf(
                    "-i", videoFile.absolutePath,
                    "-i", ttsFile.absolutePath,
                    "-filter_complex", filterComplex,
                    "-map", "0:v",
                    "-map", "[aout]",
                    "-c:v", "copy",
                    "-c:a", "aac",
                    "-b:a", AUDIO_BITRATE,
                    "-y",
                    outputFile.absolutePath
                )

                Log.d(
                    TAG,
                    "Executing FFmpeg command (attempt ${attempt + 1}): ${cmd.joinToString(" ")}"
                )

                val rc = com.arthenica.mobileffmpeg.FFmpeg.execute(cmd)
                Log.d(TAG, "FFmpeg return code: $rc")

                if (rc != com.arthenica.mobileffmpeg.Config.RETURN_CODE_SUCCESS) {
                    Log.e(TAG, "FFmpeg merge failed (rc=$rc), attempt ${attempt + 1}")
                    attempt++
                    if (attempt == MAX_FFMPEG_RETRIES) {
                        Log.e(TAG, "FFmpeg merge failed after $MAX_FFMPEG_RETRIES attempts, copying original video")
                        videoFile.copyTo(outputFile, overwrite = true)
                        withContext(Dispatchers.Main) {
                            showToast("Failed to merge audio after $MAX_FFMPEG_RETRIES attempts, using original video")
                        }
                    }
                    continue
                } else if (!outputFile.exists() || outputFile.length() == 0L) {
                    Log.e(TAG, "FFmpeg merge succeeded but output file is missing or empty")
                    videoFile.copyTo(outputFile, overwrite = true)
                    withContext(Dispatchers.Main) {
                        showToast("Output video is invalid, using original video")
                    }
                    return@withContext
                }
                return@withContext
            } catch (e: Exception) {
                Log.e(TAG, "FFmpeg merge exception (attempt ${attempt + 1}): ${e.message}", e)
                attempt++
                if (attempt == MAX_FFMPEG_RETRIES) {
                    Log.e(TAG, "FFmpeg merge failed after $MAX_FFMPEG_RETRIES attempts, copying original video")
                    videoFile.copyTo(outputFile, overwrite = true)
                    withContext(Dispatchers.Main) {
                        showToast("Merge error after $MAX_FFMPEG_RETRIES attempts: ${e.message}")
                    }
                    throw e
                }
            }
        }
    }

    private fun hasAudio(file: File): Boolean {
        return try {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(file.absolutePath)
                val hasAudio =
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
                Log.d(TAG, "Video has audio track: $hasAudio")
                hasAudio
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking audio track: ${e.message}")
            false
        }
    }

    private fun getVideoDuration(file: File): Float {
        return try {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(file.absolutePath)
                val durationMs =
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                durationMs / 1000f
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving video duration: ${e.message}")
            0f
        }
    }

    private fun playVideo(file: File) {
        if (!file.exists() || file.length() == 0L) {
            Log.e(TAG, "Video file not found or empty: ${file.absolutePath}")
            showToast("Video not found")
            return
        }
        val mediaItem = MediaItem.fromUri(Uri.fromFile(file))
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        Log.d(TAG, "Playing video from: ${file.absolutePath}, size: ${file.length()}")
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }

    private fun finishProcessing() {
        progressDialog?.dismiss()
        binding.progressBar.visibility = View.GONE
    }

    private fun cleanupTempFiles(vararg files: File) {
        files.forEach { file ->
            if (file.exists()) {
                try {
                    file.delete()
                    Log.d(TAG, "Deleted temporary file: ${file.absolutePath}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to delete temporary file ${file.absolutePath}: ${e.message}")
                }
            }
        }
    }
}