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
    private val dateFormat = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat =
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    }

    private fun logFile(context: Context): File {
        val dir = context.filesDir
        if (!dir.exists()) dir.mkdirs()
        return File(dir, Constants.LOG_FILE_NAME)
    }

    fun append(context: Context, text: String) {
        try {
            val file = logFile(context)
            val time = dateFormat.get()!!.format(Date())
            val line = "[$time] ${if (text.length > Constants.MAX_LOG_LINE_LENGTH) text.take(Constants.MAX_LOG_LINE_LENGTH) + "…(截断)" else text}"
            synchronized(this) {
                appendWithHeadInsert(file, line)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    private fun appendWithHeadInsert(file: File, line: String) {
        val maxKeep = Constants.MAX_LOG_ENTRIES - 1
        val existingLines = ArrayList<String>(maxKeep)

        if (file.exists() && file.length() > 0) {
            try {
                BufferedReader(InputStreamReader(FileInputStream(file), "UTF-8")).use { reader ->
                    var currentLine: String?
                    while (reader.readLine().also { currentLine = it } != null && existingLines.size < maxKeep) {
                        if (currentLine!!.isNotBlank()) {
                            existingLines.add(currentLine!!)
                        }
                    }
                }
            } catch (_: Exception) { }
        }

        FileOutputStream(file, false).use { fos ->
            OutputStreamWriter(fos, "UTF-8").use { writer ->
                writer.write(line)
                writer.write('\n')
                for (existing in existingLines) {
                    writer.write(existing)
                    writer.write('\n')
                }
            }
        }
    }

    fun readAll(context: Context): List<String> {
        try {
            val file = logFile(context)
            if (!file.exists()) return emptyList()
            synchronized(this) {
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
            synchronized(this) {
                if (file.exists()) file.writeText("")
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    fun latest(context: Context): String {
        try {
            val file = logFile(context)
            if (!file.exists()) return "暂无日志"
            synchronized(this) {
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
        return "暂无日志"
    }
}
