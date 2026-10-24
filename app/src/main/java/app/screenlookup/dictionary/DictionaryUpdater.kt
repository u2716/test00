package app.screenlookup.dictionary

import android.content.Context
import app.screenlookup.Di
import android.util.Log
import chimahon.HoshiDicts
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

object DictionaryUpdater {
    private const val TAG = "DictUpdater"

    data class UpdateInfo(
        val name: String, 
        val url: String, 
        val currentRev: String, 
        val newRev: String,
        val tempZipFile: File
    )

    suspend fun checkForUpdates(context: Context, httpClient: OkHttpClient): List<UpdateInfo> = withContext(Dispatchers.IO) {
        val updates = mutableListOf<UpdateInfo>()
        val dictDir = File(context.getExternalFilesDir(null), "dictionaries")
        if (!dictDir.isDirectory) return@withContext emptyList()

        listOf("term", "frequency", "pitch", "kanji").forEach { type ->
            val typeDir = File(dictDir, type)
            typeDir.listFiles()?.filter { it.isDirectory }?.forEach { dict ->
                val indexFile = File(dict, "index.json")
                if (!indexFile.exists()) return@forEach
                
                try {
                    val json = JSONObject(indexFile.readText())
                    val url = json.optString("downloadUrl")
                    val currentRev = json.optString("revision")
                    if (url.isBlank() || currentRev.isBlank()) return@forEach

                    // Download zip to temp file
                    val tempZip = File(context.cacheDir, "check_${dict.name}_${System.currentTimeMillis()}.zip")
                    val req = Request.Builder().url(url).build()
                    httpClient.newCall(req).execute().use { res ->
                        if (!res.isSuccessful) return@forEach
                        res.body?.byteStream()?.use { input ->
                            tempZip.outputStream().use { out -> input.copyTo(out) }
                        }
                    }

                    // Unzip index.json
                    var newRev = ""
                    ZipFile(tempZip).use { zip ->
                        val entry = zip.entries().asSequence().firstOrNull { it.name == "index.json" }
                        if (entry != null) {
                            zip.getInputStream(entry).bufferedReader().use { reader ->
                                val remoteJson = JSONObject(reader.readText())
                                newRev = remoteJson.optString("revision")
                            }
                        }
                    }

                    if (newRev.isNotBlank() && newRev != currentRev) {
                        updates.add(UpdateInfo(dict.name, url, currentRev, newRev, tempZip))
                    } else {
                        tempZip.delete() // Up to date, discard
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to check update for ${dict.name}", e)
                }
            }
        }
        updates.distinctBy { it.name }
    }

    suspend fun installUpdate(context: Context, update: UpdateInfo): Boolean = withContext(Dispatchers.IO) {
        try {
            val tempDir = File(context.cacheDir, "update_tmp_${System.currentTimeMillis()}")
            tempDir.mkdirs()
            
            val result = HoshiDicts.importDictionary(update.tempZipFile.absolutePath, tempDir.absolutePath)
            if (!result.success) return@withContext false
            
            val importedDir = tempDir.listFiles()?.firstOrNull { it.isDirectory } ?: return@withContext false
            val title = if (result.title.isNotBlank()) result.title else importedDir.name

            // Delete old, move new
            val dictDir = File(context.getExternalFilesDir(null), "dictionaries")
            
            val targets = buildList {
                if (result.termCount > 0) add("term")
                if (result.freqCount > 0) add("frequency")
                if (result.pitchCount > 0) add("pitch")
                if (result.kanjiCount > 0) add("kanji")
            }
            
            listOf("term", "frequency", "pitch", "kanji").forEach { type ->
                val oldDir = File(File(dictDir, type), update.name)
                if (oldDir.exists()) oldDir.deleteRecursively()
                
                if (type in targets) {
                    val dest = File(File(dictDir, type), title)
                    if (dest.exists()) dest.deleteRecursively()
                    importedDir.copyRecursively(dest, overwrite = true)
                }
            }
            
            update.tempZipFile.delete()
            tempDir.deleteRecursively()

            // If the dictionary name changed (e.g. date version in title), update the profile in place
            if (update.name != title) {
                val prefs = Di.dictionaryPreferences
                val profile = prefs.profileStore.getActiveProfile()
                val newOrder = profile.dictionaryOrder.map { if (it == update.name) title else it }
                val newEnabled = profile.enabledDictionaries.map { if (it == update.name) title else it }.toSet()
                val newDisplayModes = profile.dictionaryDisplayModes.mapKeys { if (it.key == update.name) title else it.key }
                prefs.profileStore.updateProfile(profile.copy(
                    dictionaryOrder = newOrder,
                    enabledDictionaries = newEnabled,
                    dictionaryDisplayModes = newDisplayModes
                ))
            }
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update ${update.name}", e)
            false
        }
    }
}
