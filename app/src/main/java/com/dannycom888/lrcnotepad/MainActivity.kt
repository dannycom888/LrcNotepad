package com.dannycom888.lrcnotepad

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * LRC记事本 — a Notepad-light single Activity.
 * Shows the full raw text of a .lrc (or .txt) file, tags included, in one
 * editable monospace area. No playback, no scanning, no network.
 */
class MainActivity : Activity() {

    private lateinit var titleView: TextView
    private lateinit var editor: EditText
    private lateinit var searchBox: EditText
    private val state = DocumentState()
    private var currentUri: Uri? = null
    private var currentName: String = "未命名.lrc"
    private var suppressWatcher = false
    private var pendingAfterSave: (() -> Unit)? = null

    companion object {
        private const val REQ_OPEN = 1001
        private const val REQ_SAVE_AS = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        updateTitle()
        if (savedInstanceState == null) {
            val data = intent?.data
            if (data != null && (intent.action == Intent.ACTION_VIEW || intent.action == Intent.ACTION_EDIT)) {
                openUri(data, intent.flags)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val data = intent.data ?: return
        confirmIfDirty { openUri(data, intent.flags) }
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        titleView = TextView(this).apply {
            textSize = 17f
            setPadding(24, 20, 24, 8)
            gravity = Gravity.START
        }
        root.addView(titleView)

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(12, 0, 12, 4)
        }
        fun addBtn(label: String, onClick: () -> Unit) {
            row.addView(Button(this).apply {
                text = label
                setOnClickListener { onClick() }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        addBtn("新建") { confirmIfDirty { newDocument() } }
        addBtn("打开") { confirmIfDirty { launchOpen() } }
        addBtn("保存") { save(toCurrent = true) }
        addBtn("另存为") { launchSaveAs() }
        root.addView(row)

        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(12, 0, 12, 4)
        }
        searchBox = EditText(this).apply {
            hint = "查找"
            textSize = 14f
            setSingleLine(true)
        }
        searchRow.addView(searchBox, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        searchRow.addView(Button(this).apply {
            text = "下一个"
            setOnClickListener { findNext() }
        })
        root.addView(searchRow)

        editor = EditText(this).apply {
            hint = "点「打开」选一个 .lrc 文件，或直接在这里输入"
            textSize = 14f
            typeface = android.graphics.Typeface.MONOSPACE
            gravity = Gravity.TOP or Gravity.START
            setPadding(24, 16, 24, 16)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (!suppressWatcher) {
                        state.currentText = s?.toString() ?: ""
                        updateTitle()
                    }
                }
            })
        }
        root.addView(editor, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        return root
    }

    private fun updateTitle() {
        val dirty = if (state.isDirty) " ●" else ""
        titleView.text = "$currentName$dirty"
        title = "LRC记事本 - $currentName$dirty"
    }

    private fun setEditorText(text: String) {
        suppressWatcher = true
        editor.setText(text)
        editor.setSelection(0)
        suppressWatcher = false
        state.currentText = text
    }

    private fun newDocument() {
        currentUri = null
        currentName = "未命名.lrc"
        state.onNew()
        setEditorText("")
        state.onNew()
        updateTitle()
    }

    private fun launchOpen() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/*", "application/octet-stream"))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, REQ_OPEN)
    }

    private fun launchSaveAs() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, currentName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, REQ_SAVE_AS)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        when (requestCode) {
            REQ_OPEN -> openUri(uri, data.flags)
            REQ_SAVE_AS -> {
                takePersistable(uri, data.flags)
                currentUri = uri
                currentName = displayName(uri) ?: currentName
                save(toCurrent = true)
            }
        }
    }

    private fun takePersistable(uri: Uri, flags: Int) {
        if (uri.scheme != "content") return
        val take = flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        if (take != 0) {
            try { contentResolver.takePersistableUriPermission(uri, take) } catch (_: Exception) {}
        }
    }

    private fun openUri(uri: Uri, flags: Int) {
        takePersistable(uri, flags)
        try {
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalStateException("无法读取文件")
            val decoded = TextCodec.decode(bytes)
            currentUri = uri
            currentName = displayName(uri) ?: "未命名.lrc"
            state.onLoaded(decoded.text)
            setEditorText(decoded.text)
            state.onLoaded(decoded.text)
            updateTitle()
            if (decoded.charsetName != "UTF-8") {
                toast("已按 ${decoded.charsetName} 编码打开，保存时转为 UTF-8")
            }
        } catch (e: Exception) {
            toast("打开失败：${e.message ?: "未知错误"}")
        }
    }

    private fun displayName(uri: Uri): String? {
        if (uri.scheme == "content") {
            var cursor: Cursor? = null
            try {
                cursor = contentResolver.query(uri, null, null, null, null)
                if (cursor != null && cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) return cursor.getString(idx)
                }
            } catch (_: Exception) {
            } finally { cursor?.close() }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun save(toCurrent: Boolean) {
        val uri = if (toCurrent) currentUri else null
        if (uri == null) { launchSaveAs(); return }
        state.currentText = editor.text.toString()
        try {
            contentResolver.openOutputStream(uri, "wt")?.use {
                it.write(TextCodec.encode(state.currentText))
            } ?: throw IllegalStateException("无法写入文件")
            state.onSaved()
            updateTitle()
            toast("已保存")
            pendingAfterSave?.invoke(); pendingAfterSave = null
        } catch (e: Exception) {
            toast("保存失败：${e.message ?: "未知错误"}")
        }
    }

    private fun findNext() {
        val q = searchBox.text.toString()
        if (q.isEmpty()) return
        val text = editor.text.toString()
        val from = if (editor.selectionEnd >= 0) editor.selectionEnd else 0
        var idx = text.indexOf(q, from)
        if (idx < 0) idx = text.indexOf(q, 0)
        if (idx < 0) { toast("没找到"); return }
        editor.requestFocus()
        editor.setSelection(idx, idx + q.length)
    }

    private fun confirmIfDirty(action: () -> Unit) {
        if (!state.isDirty) { action(); return }
        AlertDialog.Builder(this)
            .setTitle("有未保存的修改")
            .setMessage("当前文件还没保存，要先保存吗？")
            .setPositiveButton("保存") { _, _ ->
                if (currentUri != null) {
                    pendingAfterSave = action
                    save(toCurrent = true)
                    if (state.isDirty) pendingAfterSave = null
                } else {
                    toast("请先用「另存为」保存")
                }
            }
            .setNegativeButton("放弃修改") { _, _ -> action() }
            .setNeutralButton("取消", null)
            .show()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (state.isDirty) {
            AlertDialog.Builder(this)
                .setTitle("有未保存的修改")
                .setMessage("退出前要保存吗？")
                .setPositiveButton("保存") { _, _ ->
                    if (currentUri != null) {
                        pendingAfterSave = { finish() }
                        save(toCurrent = true)
                        if (state.isDirty) pendingAfterSave = null
                    } else toast("请先用「另存为」保存")
                }
                .setNegativeButton("直接退出") { _, _ -> finish() }
                .setNeutralButton("取消", null)
                .show()
        } else {
            @Suppress("DEPRECATION") super.onBackPressed()
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
