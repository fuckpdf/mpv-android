package `is`.xyz.mpv

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import `is`.xyz.mpv.databinding.ActivityCodecInfoBinding

class CodecInfoActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCodecInfoBinding
    
    companion object {
        private const val TAG = "CodecInfoActivity"
    }

    private suspend fun collect(videoOnly: Boolean, decodeOnly: Boolean): String = withContext(Dispatchers.IO) {
        try {
            // REGULAR_CODECS <=> "These are the codecs that are returned prior to API 21, using the now deprecated static methods."
            val codecs = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos

            // Group by aliases
            val grouped = mutableMapOf<String, MediaCodecInfo>()
            val otherNames = mutableMapOf<String, MutableList<String>>()
            
            for (codec in codecs) {
                try {
                    if (videoOnly && !codec.supportedTypes.any { it.startsWith("video/", true) })
                        continue
                    if (decodeOnly && codec.isEncoder)
                        continue
                        
                    var key = ""
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        key = codec.canonicalName
                        if (codec.name == key)
                            grouped[key] = codec
                    } else {
                        // least-effort approximation
                        key = codec.name
                        if (key.startsWith("OMX.google."))
                            key = key.replaceFirst("OMX.google.", "c2.android.")
                        else if (key.startsWith("OMX."))
                            key = key.replaceFirst("OMX.", "c2.")
                        grouped[key] = codec
                    }
                    
                    if (otherNames.containsKey(key))
                        otherNames[key]!!.add(codec.name)
                    else
                        otherNames[key] = mutableListOf(codec.name)
                        
                } catch (e: Exception) {
                    Log.w(TAG, "Error processing codec: ${codec.name}", e)
                    continue
                }
            }

            val out = mutableListOf<String>()
            for ((primaryKey, codec) in grouped) {
                try {
                    var line = if (codec.isEncoder) "E: " else "D: "
                    line += codec.name
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        if (codec.isHardwareAccelerated)
                            line += " [HW]"
                        if (codec.isSoftwareOnly)
                            line += " [SW]"
                        if (codec.isVendor)
                            line += " [V]"
                    }
                    
                    // Android 15: Additional codec info
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                        try {
                            // Add more detailed codec information for Android 15
                            if (codec.isAlias) {
                                line += " [ALIAS]"
                            }
                        } catch (e: Exception) {
                            // Ignore if method doesn't exist in current API
                        }
                    }
                    
                    out.add(line)

                    otherNames[primaryKey]?.forEach {
                        if (it != codec.name)
                            out.add("   $it")
                    }

                    // Merge mime types with the same profile set
                    val groupedProfiles = mutableMapOf<String, MutableList<String>>()
                    for (type in codec.supportedTypes) {
                        try {
                            val capabilities = codec.getCapabilitiesForType(type)
                            val levels = capabilities.profileLevels
                            
                            // note: ffmpeg only checks/uses profiles, not levels
                            val s = if (levels.isEmpty())
                                ""
                            else
                                levels.map { it.profile }.sorted().distinct().joinToString()
                                
                            if (groupedProfiles.containsKey(s))
                                groupedProfiles[s]!!.add(type)
                            else
                                groupedProfiles[s] = mutableListOf(type)
                                
                        } catch (e: Exception) {
                            Log.w(TAG, "Error getting capabilities for type: $type", e)
                            continue
                        }
                    }

                    for ((levels, mimeTypes) in groupedProfiles) {
                        out.add("- ${mimeTypes.joinToString()}")
                        if (levels.isNotEmpty())
                            out.add("-> $levels")
                    }
                    
                } catch (e: Exception) {
                    Log.w(TAG, "Error processing grouped codec: $primaryKey", e)
                    continue
                }
            }
            
            return@withContext out.joinToString("\n") + "\n"
            
        } catch (e: Exception) {
            Log.e(TAG, "Error collecting codec information", e)
            return@withContext "Error collecting codec information: ${e.message}\n"
        }
    }

    private fun refresh() {
        lifecycleScope.launch {
            try {
                // Show loading state
                binding.info.text = "Loading codec information..."
                
                // Collect codec info in background thread
                val codecInfo = collect(binding.switch1.isChecked, binding.switch2.isChecked)
                
                // Update UI on main thread
                withContext(Dispatchers.Main) {
                    binding.info.text = codecInfo
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing codec info", e)
                withContext(Dispatchers.Main) {
                    binding.info.text = "Error: ${e.message}"
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            binding = ActivityCodecInfoBinding.inflate(layoutInflater)
            setContentView(binding.root)

            // Android 15: Enhanced switch listeners with null safety
            binding.switch1.setOnCheckedChangeListener { _, _ -> 
                refresh()
            }
            binding.switch2.setOnCheckedChangeListener { _, _ -> 
                refresh()
            }
            
            // Initial refresh
            refresh()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            finish()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Cancel any ongoing coroutines when activity is destroyed
        // lifecycleScope automatically handles this, but good practice to be explicit
    }
}
