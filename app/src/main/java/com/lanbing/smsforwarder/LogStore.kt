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
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * 日志存储工具
 *
 * 优化说明：
 * - 使用单线程顺序写入，避免 synchronized 持锁阻塞调用线程
 * - 统一采用流式读写，不再区分大小文件，内存占用恒定
 * - newest-first 顺序（最新日志在文件顶部）
 */
object LogStore {

    private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    // 单线程顺序执行器：替代 synchronized，避免调用线程被阻塞在文件 I/O 上
    private val writeExecutor = Executors.newSingleThreadExecutor()

    private fun logFile(context: Context): File {
        val dir = context.filesDir
        if (!dir.exists()) dir.mkdirs()
        return File(dir, Constants.LOG_FILE_NAME)
    }

    /**
     * 追加一条日志（异步写入，立即返回）
     */
    fun append(context: Context, text: String) {
        val time = sdf.format(Date())
        val line = "[$time] ${if (text.length > Constants.MAX_LOG_LINE_LENGTH) text.take(Constants.MAX_LOG_LINE_LENGTH) + "…(截断)" else text}"
        val file = logFile(context)
        // 提交到单线程写入器，调用方不阻塞
        writeExecutor.execute {
            try {
                appendLineToFile(file, line)
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }
    }

    /**
     * 向日志文件头部插入一行新日志，并保留最多 MAX_LOG_ENTRIES 条
     * 采用流式写入，内存占用恒定（不读取整个文件到内存）
     */
    private fun appendLineToFile(file: File, line: String) {
        val tempFile = File(file.parentFile, "${file.name}.tmp")
        try {
            BufferedWriter(OutputStreamWriter(FileOutputStream(tempFile), "UTF-8")).use { writer ->
                writer.write(line)
                writer.newLine()

                var count = 0
                if (file.exists()) {
                    BufferedReader(InputStreamReader(FileInputStream(file), "UTF-8")).use { reader ->
                        var currentLine: String?
                        while (reader.readLine().also { currentLine = it } != null
                            && count < Constants.MAX_LOG_ENTRIES - 1) {
                            if (currentLine!!.isNotBlank()) {
                                writer.write(currentLine!!)
                                writer.newLine()
                                count++
                            }
                        }
                    }
                }
            }
            // 原子替换
            if (tempFile.exists()) {
                file.delete()
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
            val lines = mutableListOf<String>()
            BufferedReader(InputStreamReader(FileInputStream(file), "UTF-8")).use { br ->
                var line: String? = br.readLine()
                while (line != null) {
                    if (line.isNotBlank()) lines.add(line)
                    line = br.readLine()
                }
            }
            return lines
        } catch (t: Throwable) {
            t.printStackTrace()
            return emptyList()
        }
    }

    fun clear(context: Context) {
        writeExecutor.execute {
            try {
                val file = logFile(context)
                if (file.exists()) file.writeText("")
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }
    }

    fun latest(context: Context): String {
        try {
            val file = logFile(context)
            if (!file.exists()) return "暂无日志"
            BufferedReader(InputStreamReader(FileInputStream(file), "UTF-8")).use { br ->
                var line: String?
                while (br.readLine().also { line = it } != null) {
                    if (line!!.isNotBlank()) {
                        return line!!
                    }
                }
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        return "暂无日志"
    }
}
