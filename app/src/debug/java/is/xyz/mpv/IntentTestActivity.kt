package `is`.xyz.mpv

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import `is`.xyz.mpv.databinding.ActivityIntentTestBinding

class IntentTestActivity : AppCompatActivity() {
    private lateinit var binding: ActivityIntentTestBinding
    
    companion object {
        private const val TAG = "IntentTestActivity"
        private const val MAX_TEXT_LENGTH = 10000 // Prevent memory issues
    }

    private val callback = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        lifecycleScope.launch {
            try {
                updateText("resultCode: ${ActivityResult.resultCodeToString(result.resultCode)}\n")
                
                val intent = result.data
                if (intent != null) {
                    updateText("action: ${intent.action}\ndata: ${intent.dataString}\n")
                    
                    // Android 15: Safe extras handling
                    val extras = intent.extras
                    if (extras != null) {
                        val keySet = try {
                            extras.keySet()
                        } catch (e: Exception) {
                            Log.w(TAG, "Error getting extras keySet", e)
                            emptySet<String>()
                        }
                        
                        for (key in keySet) {
                            try {
                                val value = extras.get(key)
                                val valueStr = when {
                                    value == null -> "null"
                                    value is String && value.length > 100 -> "${value.take(97)}..."
                                    value is ByteArray -> "ByteArray[${value.size}]"
                                    else -> value.toString()
                                }
                                updateText("extras[$key] = $valueStr\n")
                            } catch (e: Exception) {
                                Log.w(TAG, "Error reading extra: $key", e)
                                updateText("extras[$key] = <error reading value>\n")
                            }
                        }
                    }
                } else {
                    updateText("No result data\n")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing activity result", e)
                updateText("Error processing result: ${e.message}\n")
            }
        }
    }

    private var text = ""

    private suspend fun updateText(append: String) {
        withContext(Dispatchers.Main) {
            try {
                text += append
                
                // Android 15: Prevent memory issues with large text
                if (text.length > MAX_TEXT_LENGTH) {
                    text = "... (truncated)\n" + text.takeLast(MAX_TEXT_LENGTH - 100)
                }
                
                binding.info.text = text
            } catch (e: Exception) {
                Log.e(TAG, "Error updating text", e)
            }
        }
    }

    private fun validateAndLaunchIntent() {
        lifecycleScope.launch {
            try {
                val uriText = binding.editText1.text?.toString()?.trim()
                if (uriText.isNullOrEmpty()) {
                    showToast("Please enter a URI")
                    return@launch
                }

                val uri = try {
                    Uri.parse(uriText)
                } catch (e: Exception) {
                    Log.w(TAG, "Invalid URI: $uriText", e)
                    showToast("Invalid URI format")
                    return@launch
                }

                if (uri.scheme.isNullOrEmpty()) {
                    showToast("URI must have a scheme (http, file, etc.)")
                    return@launch
                }

                // Android 15: Enhanced intent creation with safety checks
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "video/any")
                    setPackage(packageName)
                    
                    // Add flags for better Android 15 compatibility
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                // Add subtitle if enabled
                if (binding.switch1.isChecked) {
                    try {
                        val subMime = "application/x-subrip"
                        val subData = "1\n00:00:00,000 --> 00:00:10,000\nHello World\n\n"
                        val subUri = Uri.parse(
                            "data:${subMime};base64," + 
                            Base64.encodeToString(subData.toByteArray(), Base64.NO_WRAP)
                        )
                        intent.putExtra("subs", arrayOf(subUri))
                        intent.putExtra("subs.enable", arrayOf(subUri))
                    } catch (e: Exception) {
                        Log.w(TAG, "Error creating subtitle data", e)
                        showToast("Warning: Could not create subtitle data")
                    }
                }

                // Add decode mode if enabled
                if (binding.switch2.isChecked) {
                    intent.putExtra("decode_mode", 2.toByte())
                }

                // Add title if enabled
                if (binding.switch3.isChecked) {
                    intent.putExtra("title", "example text")
                }

                // Add position if set
                val seekPosition = binding.seekBar2.progress
                if (seekPosition > 0) {
                    intent.putExtra("position", seekPosition * 1000)
                }

                // Android 15: Check if intent can be resolved before launching
                val resolveInfo = packageManager.resolveActivity(intent, 0)
                if (resolveInfo == null) {
                    showToast("No app found to handle this intent")
                    return@launch
                }

                // Launch intent
                callback.launch(intent)
                
                text = ""
                updateText("Intent launched successfully!\n")
                updateText("URI: $uriText\n")
                updateText("Package: $packageName\n")
                updateText("Waiting for result...\n\n")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error launching intent", e)
                showToast("Error launching intent: ${e.message}")
                updateText("Error: ${e.message}\n")
            }
        }
    }

    private suspend fun showToast(message: String) {
        withContext(Dispatchers.Main) {
            Toast.makeText(this@IntentTestActivity, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            binding = ActivityIntentTestBinding.inflate(layoutInflater)
            setContentView(binding.root)

            // Android 15: Enhanced button click with validation
            binding.button.setOnClickListener {
                validateAndLaunchIntent()
            }
            
            // Android 15: Set default URI for testing
            if (binding.editText1.text.isNullOrEmpty()) {
                binding.editText1.setText("content://")
            }
            
            // Initialize text
            text = "Intent Test Activity Ready\n\n"
            binding.info.text = text
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            Toast.makeText(this, "Error initializing activity", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Android 15: Save current text state
        outState.putString("current_text", text)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        // Android 15: Restore text state
        text = savedInstanceState.getString("current_text", "")
        binding.info.text = text
    }
}
