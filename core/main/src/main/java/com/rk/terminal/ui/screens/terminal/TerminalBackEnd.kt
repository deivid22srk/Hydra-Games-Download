package com.rk.terminal.ui.screens.terminal

import android.content.res.Configuration
import android.content.res.Resources
import android.media.MediaPlayer
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.lifecycle.lifecycleScope
import com.blankj.utilcode.util.ClipboardUtils
import com.blankj.utilcode.util.KeyboardUtils
import com.rk.libcommons.child
import com.rk.libcommons.createFileIfNot
import com.rk.libcommons.dpToPx
import com.rk.settings.Settings
import com.rk.terminal.ui.activities.terminal.MainActivity
import com.rk.terminal.ui.screens.home.activeDownloads
import com.rk.terminal.ui.screens.terminal.virtualkeys.SpecialButton
import com.rk.terminal.ui.screens.terminal.virtualkeys.VirtualKeysView
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class TerminalBackEnd(val terminal: TerminalView,val activity: MainActivity) : TerminalViewClient, TerminalSessionClient {
    var sessionId: String? = null

    override fun onTextChanged(changedSession: TerminalSession) {
        terminal.onScreenUpdated()
        if (sessionId == "GoFileDownload" || sessionId == "BuzzHeavierDownload" || sessionId == "Aria2Download") {
            updateDownloadProgress(changedSession)
        }
    }

    private val progressRegex = Regex("""\[#([a-f0-9]{6,16}).*?(\d+)%""")
    private val percentRegex = Regex("""(\d+)%""")

    private fun updateDownloadProgress(session: TerminalSession) {
        val text = session.emulator.screen.getSelectedText(0, 0, session.emulator.mColumns, session.emulator.mRows)
        val lines = text.split("\n")
        val lastLines = lines.takeLast(10)

        for (line in lastLines.reversed()) {
            val matchResult = progressRegex.find(line) ?: percentRegex.find(line)
            if (matchResult != null) {
                val progressPercent = matchResult.groupValues.last().toFloatOrNull() ?: continue
                val progressValue = progressPercent / 100f
                val gidMatch = if (matchResult.groupValues.size >= 3) matchResult.groupValues[1] else null

                activity.runOnUiThread {
                    val index = activeDownloads.indexOfFirst {
                        (gidMatch != null && (it.id.startsWith(gidMatch) || it.gid?.startsWith(gidMatch) == true)) ||
                        it.title.contains(if (sessionId == "GoFileDownload") "GoFile" else "BuzzHeavier", ignoreCase = true)
                    }

                    if (index != -1) {
                        val current = activeDownloads[index]
                        activeDownloads[index] = current.copy(progress = progressValue, status = line.trim())
                    }
                }
                break
            }
        }
    }
    
    override fun onTitleChanged(changedSession: TerminalSession) {

    }
    
    override fun onSessionFinished(finishedSession: TerminalSession) {
        val id = activity.sessionBinder?.getService()?.sessionList?.entries?.find {
            activity.sessionBinder?.getSession(it.key) == finishedSession
        }?.key ?: sessionId

        if (id == "GoFileDownload" || id == "BuzzHeavierDownload" || id == "Aria2Download") {
            activity.runOnUiThread {
                val index = activeDownloads.indexOfFirst {
                    it.id.contains(id.replace("Aria2Download_", "")) ||
                    it.title.contains(if (id.contains("GoFile")) "GoFile" else "BuzzHeavier", ignoreCase = true)
                }
                if (index != -1) {
                    val current = activeDownloads[index]
                    val isSuccess = finishedSession.exitStatus == 0
                    activeDownloads[index] = current.copy(
                        progress = 1f,
                        isCompleted = true,
                        status = if (isSuccess) "Download concluído" else "Download falhou (código ${finishedSession.exitStatus})"
                    )
                }
                val status = if (finishedSession.exitStatus == 0) "concluído com sucesso" else "falhou (código ${finishedSession.exitStatus})"
                android.widget.Toast.makeText(activity, "Download $id $status", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }
    
    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
        ClipboardUtils.copyText("Terminal", text)
    }
    
    override fun onPasteTextFromClipboard(session: TerminalSession?) {
        val clip = ClipboardUtils.getText().toString()
        if (clip.trim { it <= ' ' }.isNotEmpty() && terminal.mEmulator != null) {
            terminal.mEmulator.paste(clip)
        }
    }

    override fun setTerminalShellPid(
        session: TerminalSession,
        pid: Int
    ) {}


    override fun onBell(session: TerminalSession) {
        if (Settings.bell){
            activity.lifecycleScope.launch{
                val bellFile = activity.cacheDir.child("bell.oga")
                if (bellFile.exists().not()){
                    bellFile.createNewFile()
                    withContext(Dispatchers.IO){
                        activity.assets.open("bell.oga").use { assetIS ->
                            FileOutputStream(bellFile).use { bellFileOutS ->
                                assetIS.copyTo(bellFileOutS)
                            }
                        }
                    }

                }

                val mediaPlayer = MediaPlayer()
                mediaPlayer.setOnCompletionListener{
                    it?.release()
                }
                mediaPlayer.setDataSource(bellFile.absolutePath)
                mediaPlayer.prepare()
                mediaPlayer.start()
            }
        }
    }
    
    override fun onColorsChanged(session: TerminalSession) {}
    
    override fun onTerminalCursorStateChange(state: Boolean) {}
    
    override fun getTerminalCursorStyle(): Int {
        return TerminalEmulator.DEFAULT_TERMINAL_CURSOR_STYLE
    }
    
    override fun logError(tag: String?, message: String?) {
        Log.e(tag.toString(), message.toString())
    }
    
    override fun logWarn(tag: String?, message: String?) {
        Log.w(tag.toString(), message.toString())
    }
    
    override fun logInfo(tag: String?, message: String?) {
        Log.i(tag.toString(), message.toString())
    }
    
    override fun logDebug(tag: String?, message: String?) {
        Log.d(tag.toString(), message.toString())
    }
    
    override fun logVerbose(tag: String?, message: String?) {
        Log.v(tag.toString(), message.toString())
    }
    
    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {
        Log.e(tag.toString(), message.toString())
        e?.printStackTrace()
    }
    
    override fun logStackTrace(tag: String?, e: Exception?) {
        e?.printStackTrace()
    }

    override fun onScale(scale: Float): Float {
        val fontScale = scale.coerceIn(11f, 45f)
        terminal.setTextSize(fontScale.toInt())
        return fontScale
    }

    val isHardwareKeyboardConnected: Boolean
        get() {
            val config = Resources.getSystem().configuration
            return config.keyboard != Configuration.KEYBOARD_NOKEYS
        }


    override fun onSingleTapUp(e: MotionEvent) {
        if (!(isHardwareKeyboardConnected && Settings.hide_soft_keyboard_if_hwd)){
            showSoftInput()
        }
    }
    
    override fun shouldBackButtonBeMappedToEscape(): Boolean {
        return false
    }
    
    override fun shouldEnforceCharBasedInput(): Boolean {
        return Settings.input_mode != 1 // TYPE_NULL mode uses TYPE_NULL inputType
    }

    override fun getInputMode(): Int {
        return Settings.input_mode
    }
    
    override fun shouldUseCtrlSpaceWorkaround(): Boolean {
        return true
    }
    
    override fun isTerminalViewSelected(): Boolean {
        return true
    }
    
    override fun copyModeChanged(copyMode: Boolean) {}
    
    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean {
        if (KeyShortcutHandler.handle(keyCode, e, activity)) {
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_ENTER && !session.isRunning) {
            activity.sessionBinder?.terminateSession(activity.sessionBinder!!.getService().currentSession.value.first)
            if (activity.sessionBinder!!.getService().sessionList.isEmpty()){
                activity.finish()
            }else{
                changeSession(activity,activity.sessionBinder!!.getService().sessionList.keys.first())
            }
            return true
        }
        return false
    }
    
    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean {
        return false
    }
    
    override fun onLongPress(event: MotionEvent): Boolean {
        return false
    }
    
    // keys
    override fun readControlKey(): Boolean {
        val state = virtualKeysView.get()?.readSpecialButton(
            SpecialButton.CTRL, true)
        return state != null && state
    }
    
    override fun readAltKey(): Boolean {
       val state = virtualKeysView.get()?.readSpecialButton(
           SpecialButton.ALT, true)
        return state != null && state
    }
    
    override fun readShiftKey(): Boolean {
        val state = virtualKeysView.get()?.readSpecialButton(
            SpecialButton.SHIFT, true)
        return state != null && state
    }
    
    override fun readFnKey(): Boolean {
        val state = virtualKeysView.get()?.readSpecialButton(
            SpecialButton.FN, true)
        return state != null && state
    }
    
    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean {
        return false
    }
    
    override fun onEmulatorSet() {
        setTerminalCursorBlinkingState(true)
    }
    
    private fun setTerminalCursorBlinkingState(start: Boolean) {
        if (terminal.mEmulator != null) {
            terminal.setTerminalCursorBlinkerState(start, true)
        }
    }
    
    private fun showSoftInput() {
        terminal.requestFocus()
        KeyboardUtils.showSoftInput(terminal)
    }
}
