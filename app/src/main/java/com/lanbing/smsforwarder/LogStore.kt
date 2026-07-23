/*
 * 短信转发助手
 *
 * 著作权人：华昊科技有限公司
 * 开发者：王士辉
 *
 * Copyright (c) 2026 华昊科技有限公司. All rights reserved.
 * 联系邮箱：huahao@email.cn
 */

package com.lanbing.smsforwarder

import android.content.Context
import android.os.FileObserver
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.onStart
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.*

object LogStore {
    private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val lock = Any()

    private fun logFile(context: Context): File {
        val dir = context.filesDir
        if (!dir.exists()) dir.mkdirs()
        return File(dir, Constants.LOG_FILE_NAME)
    }

    fun append(context: Context, text: String) {
        try {
            val file = logFile(context)
            val time = sdf.format(Date())
            val line = "[$time] ${if (text.length > Constants.MAX_LOG_LINE_LENGTH) text.take(Constants.MAX_LOG_LINE_LENGTH) + "…(截断)" else text}"
            synchronized(lock) {
                // 优化：使用 RandomAccessFile 或分批读取大文件
                if (file.exists() && file.length() > 1024 * 1024) { // 如果文件超过 1MB，分批处理
                    appendLargeFile(file, line)
                } else {
                    appendSmallFile(file, line)
                }
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    private fun appendSmallFile(file: File, line: String) {
        // 小文件保持原有逻辑
        val existing = if (file.exists()) file.readText() else ""
        val newContent = line + "\n" + existing
        val lines = newContent.lines().filter { it.isNotBlank() }
        val limited = if (lines.size > Constants.MAX_LOG_ENTRIES) lines.take(Constants.MAX_LOG_ENTRIES) else lines
        file.writeText(limited.joinToString("\n"))
    }

    private fun appendLargeFile(file: File, line: String) {
        // 大文件优化：只读取前 N 行，避免加载整个文件
        val tempFile = File(file.parentFile, "${file.name}.tmp")
        try {
            BufferedWriter(OutputStreamWriter(FileOutputStream(tempFile), "UTF-8")).use { writer ->
                writer.write(line)
                writer.newLine()
                
                // 读取现有文件的前 MAX_ENTRIES - 1 行
                var count = 0
                BufferedReader(InputStreamReader(FileInputStream(file), "UTF-8")).use { reader ->
                    var currentLine: String?
                    while (reader.readLine().also { currentLine = it } != null && count < Constants.MAX_LOG_ENTRIES - 1) {
                        if (currentLine!!.isNotBlank()) {
                            writer.write(currentLine!!)
                            writer.newLine()
                            count++
                        }
                    }
                }
            }
            // 原子性替换
            if (tempFile.exists() && file.delete()) {
                tempFile.renameTo(file)
            }
        } finally {
            if (tempFile.exists()) tempFile.delete()
        }
    }

    fun readAll(context: Context): List<String> {
        try {
            val file = logFile(context)
            if (!file.exists()) return emptyList()
            synchronized(lock) {
                val lines = mutableListOf<String>()
                BufferedReader(InputStreamReader(FileInputStream(file), "UTF-8")).use { br ->
                    var line: String? = br.readLine()
                    while (line != null) {
                        if (line.isNotBlank()) lines.add(line)
                        line = br.readLine()
                    }
                }
                return lines
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            return emptyList()
        }
    }

    fun clear(context: Context) {
        try {
            val file = logFile(context)
            synchronized(lock) {
                if (file.exists()) file.writeText("")
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    fun latest(context: Context): String {
        try {
            val file = logFile(context)
            if (!file.exists()) return context.getString(R.string.no_logs)
            synchronized(lock) {
                BufferedReader(InputStreamReader(FileInputStream(file), "UTF-8")).use { br ->
                    var line: String?
                    while (br.readLine().also { line = it } != null) {
                        if (line!!.isNotBlank()) {
                            return line!!
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        return context.getString(R.string.no_logs)
    }

    /**
     * 监听日志文件变化，返回增量更新的 Flow
     * 初始发射所有日志，之后只发射新增的日志条目
     */
    fun observeLogs(context: Context): Flow<List<String>> = callbackFlow {
        val file = logFile(context)
        val parentDir = file.parent ?: context.filesDir.absolutePath
        
        // 记录当前日志数量，用于增量更新
        var lastLineCount = 0
        
        @Suppress("DEPRECATION")
        val observer = object : android.os.FileObserver(parentDir, FileObserver.MODIFY or FileObserver.CREATE) {
            override fun onEvent(event: Int, path: String?) {
                if (path == Constants.LOG_FILE_NAME && event and (MODIFY or CREATE) != 0) {
                    try {
                        val newLines = readAll(context)
                        // 只发送新增的日志（新日志在文件开头）
                        if (newLines.size > lastLineCount) {
                            val addedLines = newLines.subList(0, newLines.size - lastLineCount)
                            trySend(addedLines)
                        }
                        lastLineCount = newLines.size
                    } catch (_: Throwable) {
                    }
                }
            }
        }
        
        observer.startWatching()
        
        // 初始发射所有日志
        val initialLogs = readAll(context)
        lastLineCount = initialLogs.size
        trySend(initialLogs)
        
        awaitClose {
            observer.stopWatching()
        }
    }.onStart {
        // 确保初始值被发送
        val initialLogs = readAll(context)
        emit(initialLogs)
    }
}