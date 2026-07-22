package com.lanbing.smsforwarder

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object NetworkClient {
    val instance: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .callTimeout(Constants.CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .connectTimeout(Constants.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(Constants.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(Constants.WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }
}