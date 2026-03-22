package com.rk.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import com.rk.libcommons.application
import com.rk.terminal.ui.screens.home.HydraProfile
import com.rk.terminal.ui.screens.settings.WorkingMode
import com.rk.terminal.ui.screens.settings.InputMode

object Settings {
    //Boolean
    var seccomp
        get() = Preference.getBoolean(key = "seccomp", default = false)
        set(value) = Preference.setBoolean(key = "seccomp",value)
    var amoled
        get() = Preference.getBoolean(key = "oled", default = false)
        set(value) = Preference.setBoolean(key = "oled",value)
    var monet
        get() = Preference.getBoolean(
            key = "monet",
            default = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        )
        set(value) = Preference.setBoolean(key = "monet",value)
    var ignore_storage_permission
        get() = Preference.getBoolean(key = "ignore_storage_permission",default = false)
        set(value) = Preference.setBoolean(key = "ignore_storage_permission",value)
    var github
        get() = Preference.getBoolean(key = "github", default = true)
        set(value) = Preference.setBoolean(key = "github",value)


   var default_night_mode
        get() = Preference.getInt(key = "default_night_mode", default = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        set(value) = Preference.setInt(key = "default_night_mode",value)

    var terminal_font_size
        get() = Preference.getInt(key = "terminal_font_size", default = 13)
        set(value) = Preference.setInt(key = "terminal_font_size",value)

    var wallTransparency
        get() = Preference.getFloat(key = "wallTransparency", default = 0f)
        set(value) = Preference.setFloat(key = "wallTransparency",value)

    var working_Mode
        get() = Preference.getInt(key = "workingMode", default = WorkingMode.ALPINE)
        set(value) = Preference.setInt(key = "workingMode",value)

    var input_mode
        get() = Preference.getInt(key = "input_mode", default = InputMode.DEFAULT)
        set(value) = Preference.setInt(key = "input_mode", value)

    var custom_background_name
        get() = Preference.getString(key = "custom_bg_name", default = "No Image Selected")
        set(value) = Preference.setString(key = "custom_bg_name",value)
    var custom_font_name
        get() = Preference.getString(key = "custom_ttf_name", default = "No Font Selected")
        set(value) = Preference.setString(key = "custom_ttf_name",value)

    var blackTextColor
        get() = Preference.getBoolean(key = "blackText", default = false)
        set(value) = Preference.setBoolean(key = "blackText",value)

    var bell
        get() = Preference.getBoolean(key = "bell", default = false)
        set(value) = Preference.setBoolean(key = "bell",value)

    var vibrate
        get() = Preference.getBoolean(key = "vibrate", default = true)
        set(value) = Preference.setBoolean(key = "vibrate",value)

    var toolbar
        get() = Preference.getBoolean(key = "toolbar", default = true)
        set(value) = Preference.setBoolean(key = "toolbar",value)

    var statusBar
        get() = Preference.getBoolean(key = "statusBar", default = true)
        set(value) = Preference.setBoolean(key = "statusBar",value)

    var horizontal_statusBar
        get() = Preference.getBoolean(key = "horizontal_statusBar", default = true)
        set(value) = Preference.setBoolean(key = "horizontal_statusBar",value)

    var toolbar_in_horizontal
        get() = Preference.getBoolean(key = "toolbar_h", default = true)
        set(value) = Preference.setBoolean(key = "toolbar_h",value)

    var virtualKeys
        get() = Preference.getBoolean(key = "virtualKeys", default = true)
        set(value) = Preference.setBoolean(key = "virtualKeys",value)

    var hide_soft_keyboard_if_hwd
        get() = Preference.getBoolean(key = "force_soft_keyboard", default = true)
        set(value) = Preference.setBoolean(key = "force_soft_keyboard",value)

    var shortcuts_enabled
        get() = Preference.getBoolean(key = "shortcuts_enabled", default = true)
        set(value) = Preference.setBoolean(key = "shortcuts_enabled", value)

    var useDownloadScripts
        get() = Preference.getBoolean(key = "use_download_scripts", default = false)
        set(value) = Preference.setBoolean(key = "use_download_scripts", value = value)

    var useGofileScript
        get() = Preference.getBoolean(key = "use_gofile_script", default = true)
        set(value) = Preference.setBoolean(key = "use_gofile_script", value = value)

    var useBuzzheavierScript
        get() = Preference.getBoolean(key = "use_buzzheavier_script", default = true)
        set(value) = Preference.setBoolean(key = "use_buzzheavier_script", value = value)

    var usePixeldrainScript
        get() = Preference.getBoolean(key = "use_pixeldrain_script", default = true)
        set(value) = Preference.setBoolean(key = "use_pixeldrain_script", value = value)

    var useMediafireScript
        get() = Preference.getBoolean(key = "use_mediafire_script", default = true)
        set(value) = Preference.setBoolean(key = "use_mediafire_script", value = value)

    var useDatanodesScript
        get() = Preference.getBoolean(key = "use_datanodes_script", default = true)
        set(value) = Preference.setBoolean(key = "use_datanodes_script", value = value)

    var useFuckingfastScript
        get() = Preference.getBoolean(key = "use_fuckingfast_script", default = true)
        set(value) = Preference.setBoolean(key = "use_fuckingfast_script", value = value)

    var useRootzScript
        get() = Preference.getBoolean(key = "use_rootz_script", default = true)
        set(value) = Preference.setBoolean(key = "use_rootz_script", value = value)

    var fallbackToBrowserOnError
        get() = Preference.getBoolean(key = "fallback_to_browser_on_error", default = true)
        set(value) = Preference.setBoolean(key = "fallback_to_browser_on_error", value = value)

    var useExternalBrowser
        get() = Preference.getBoolean(key = "use_external_browser", default = false)
        set(value) = Preference.setBoolean(key = "use_external_browser", value = value)

    var selectedExternalBrowserPackage
        get() = Preference.getString(key = "selected_external_browser_pkg", default = "")
        set(value) = Preference.setString(key = "selected_external_browser_pkg", value = value)

    var hydraSources: List<com.rk.terminal.ui.screens.home.HydraSourceConfig>
        get() {
            val json = Preference.getString(key = "hydra_sources_v2", default = "[]")
            return try {
                val type = object : com.google.gson.reflect.TypeToken<List<com.rk.terminal.ui.screens.home.HydraSourceConfig>>() {}.type
                com.google.gson.Gson().fromJson<List<com.rk.terminal.ui.screens.home.HydraSourceConfig>>(json, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
        set(value) {
            val json = com.google.gson.Gson().toJson(value)
            Preference.setString(key = "hydra_sources_v2", value = json)
        }

    var downloadPath: String
        get() = Preference.getString(key = "download_path", default = "/sdcard/Download")
        set(value) = Preference.setString(key = "download_path", value = value)

    var steamGridDbApiKey: String
        get() = Preference.getString(key = "sgdb_api_key", default = "908de574ad3e4939f500725fd24e47b9")
        set(value) = Preference.setString(key = "sgdb_api_key", value = value)

    var aria2RpcSecret: String
        get() = Preference.getString(key = "aria2_rpc_secret", default = "")
        set(value) = Preference.setString(key = "aria2_rpc_secret", value = value)

    var aria2RpcPort: Int
        get() = Preference.getInt(key = "aria2_rpc_port", default = 6800)
        set(value) = Preference.setInt(key = "aria2_rpc_port", value = value)

    var aria2MaxConnections: Int
        get() = Preference.getInt(key = "aria2_max_connections", default = 5)
        set(value) = Preference.setInt(key = "aria2_max_connections", value = value)

    var aria2UserAgent: String
        get() = Preference.getString(key = "aria2_user_agent", default = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        set(value) = Preference.setString(key = "aria2_user_agent", value = value)

    var aria2MaxTries: Int
        get() = Preference.getInt(key = "aria2_max_tries", default = 10)
        set(value) = Preference.setInt(key = "aria2_max_tries", value = value)

    var aria2RetryWait: Int
        get() = Preference.getInt(key = "aria2_retry_wait", default = 5)
        set(value) = Preference.setInt(key = "aria2_retry_wait", value = value)

    var aria2Timeout: Int
        get() = Preference.getInt(key = "aria2_timeout", default = 60)
        set(value) = Preference.setInt(key = "aria2_timeout", value = value)

    var aria2FileAllocation: String
        get() = Preference.getString(key = "aria2_file_allocation", default = "none")
        set(value) = Preference.setString(key = "aria2_file_allocation", value = value)

    var aria2MinSplitSize: String
        get() = Preference.getString(key = "aria2_min_split_size", default = "20M")
        set(value) = Preference.setString(key = "aria2_min_split_size", value = value)

    var aria2MaxDownloadLimit: String
        get() = Preference.getString(key = "aria2_max_download_limit", default = "0")
        set(value) = Preference.setString(key = "aria2_max_download_limit", value = value)

    var aria2ContinueDownload: Boolean
        get() = Preference.getBoolean(key = "aria2_continue_download", default = true)
        set(value) = Preference.setBoolean(key = "aria2_continue_download", value = value)

    var aria2AutoSaveInterval: Int
        get() = Preference.getInt(key = "aria2_auto_save_interval", default = 60)
        set(value) = Preference.setInt(key = "aria2_auto_save_interval", value = value)

    var isExtraSetupComplete: Boolean
        get() = Preference.getBoolean(key = "is_extra_setup_complete", default = false)
        set(value) = Preference.setBoolean(key = "is_extra_setup_complete", value = value)

    var accessToken: String
        get() = Preference.getString(key = "hydra_access_token", default = "")
        set(value) = Preference.setString(key = "hydra_access_token", value = value)

    var refreshToken: String
        get() = Preference.getString(key = "hydra_refresh_token", default = "")
        set(value) = Preference.setString(key = "hydra_refresh_token", value = value)

    var tokenExpiration: Long
        get() = Preference.getLong(key = "hydra_token_expiration", default = 0L)
        set(value) = Preference.setLong(key = "hydra_token_expiration", value = value)

    var userId: String
        get() = Preference.getString(key = "hydra_user_id", default = "")
        set(value) = Preference.setString(key = "hydra_user_id", value = value)

    var userDisplayName: String
        get() = Preference.getString(key = "hydra_user_display_name", default = "")
        set(value) = Preference.setString(key = "hydra_user_display_name", value = value)

    var userProfileImageUrl: String
        get() = Preference.getString(key = "hydra_user_profile_image_url", default = "")
        set(value) = Preference.setString(key = "hydra_user_profile_image_url", value = value)

    var userBio: String
        get() = Preference.getString(key = "hydra_user_bio", default = "")
        set(value) = Preference.setString(key = "hydra_user_bio", value = value)

    var userBackgroundImageUrl: String
        get() = Preference.getString(key = "hydra_user_background_image_url", default = "")
        set(value) = Preference.setString(key = "hydra_user_background_image_url", value = value)

    fun updateFromProfile(profile: HydraProfile) {
        if (profile.id != null) userId = profile.id
        userDisplayName = profile.displayName ?: ""
        userProfileImageUrl = profile.profileImageUrl ?: ""
        userBio = profile.bio ?: ""
        userBackgroundImageUrl = profile.backgroundImageUrl ?: ""
    }

    fun getShortcutBinding(action: com.rk.terminal.ui.screens.terminal.ShortcutAction): com.rk.terminal.ui.screens.terminal.ShortcutBinding {
        val raw = Preference.getString(key = action.prefKey, default = action.default.serialize())
        return com.rk.terminal.ui.screens.terminal.ShortcutBinding.deserialize(raw)
    }

    fun setShortcutBinding(action: com.rk.terminal.ui.screens.terminal.ShortcutAction, binding: com.rk.terminal.ui.screens.terminal.ShortcutBinding) {
        Preference.setString(key = action.prefKey, value = binding.serialize())
    }



}

object Preference {
    private var sharedPreferences: SharedPreferences = application!!.getSharedPreferences("Settings", Context.MODE_PRIVATE)

    //store the result into memory for faster access
    private val stringCache = hashMapOf<String, String?>()
    private val boolCache = hashMapOf<String, Boolean>()
    private val intCache = hashMapOf<String, Int>()
    private val longCache = hashMapOf<String, Long>()
    private val floatCache = hashMapOf<String, Float>()

    @SuppressLint("ApplySharedPref")
    fun clearData(){
        sharedPreferences.edit().clear().commit()
    }

    fun removeKey(key: String){
        sharedPreferences.edit().remove(key).apply()

        if (stringCache.containsKey(key)){
            stringCache.remove(key)
            return
        }

        if (boolCache.containsKey(key)){
            boolCache.remove(key)
            return
        }

        if (intCache.containsKey(key)){
            intCache.remove(key)
            return
        }

        if (longCache.containsKey(key)){
            longCache.remove(key)
            return
        }

        if (floatCache.containsKey(key)){
            floatCache.remove(key)
            return
        }
    }

    fun getBoolean(key: String, default: Boolean): Boolean {
        runCatching {
            return boolCache[key] ?: sharedPreferences.getBoolean(key, default)
                .also { boolCache[key] = it }
        }.onFailure {
            it.printStackTrace()
            setBoolean(key, default)
        }
        return default
    }

    fun setBoolean(key: String, value: Boolean) {
        boolCache[key] = value
        runCatching {
            val editor = sharedPreferences.edit()
            editor.putBoolean(key, value)
            editor.apply()
        }.onFailure { it.printStackTrace() }
    }



    fun getString(key: String, default: String): String {
        runCatching {
            return stringCache[key] ?: sharedPreferences.getString(key, default)!!
                .also { stringCache[key] = it }
        }.onFailure {
            it.printStackTrace()
            setString(key, default)
        }
        return default
    }
    fun setString(key: String, value: String?) {
        stringCache[key] = value
        runCatching {
            val editor = sharedPreferences.edit()
            editor.putString(key, value)
            editor.apply()
        }.onFailure {
            it.printStackTrace()
        }
    }

    fun getInt(key: String, default: Int): Int {
        runCatching {
            return intCache[key] ?: sharedPreferences.getInt(key, default)
                .also { intCache[key] = it }
        }.onFailure {
            it.printStackTrace()
            setInt(key, default)
        }
        return default
    }

    fun setInt(key: String, value: Int) {
        intCache[key] = value
        runCatching {
            val editor = sharedPreferences.edit()
            editor.putInt(key, value)
            editor.apply()
        }.onFailure {
            it.printStackTrace()
        }

    }

    fun getLong(key: String, default: Long): Long {
        runCatching {
            return longCache[key] ?: sharedPreferences.getLong(key, default)
                .also { longCache[key] = it }
        }.onFailure {
            it.printStackTrace()
            setLong(key, default)
        }
        return default
    }

    fun setLong(key: String, value: Long) {
        longCache[key] = value
        runCatching {
            val editor = sharedPreferences.edit()
            editor.putLong(key,value)
            editor.apply()
        }.onFailure {
            it.printStackTrace()
        }
    }

    fun getFloat(key: String, default: Float): Float {
        runCatching {
            return floatCache[key] ?: sharedPreferences.getFloat(key, default)
                .also { floatCache[key] = it }
        }.onFailure {
            it.printStackTrace()
            setFloat(key, default)
        }
        return default
    }

    fun setFloat(key: String, value: Float) {
        floatCache[key] = value
        runCatching {
            val editor = sharedPreferences.edit()
            editor.putFloat(key,value)
            editor.apply()
        }.onFailure {
            it.printStackTrace()
        }
    }

}
