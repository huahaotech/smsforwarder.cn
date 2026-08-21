# ProGuard / R8 规则文件（激进裁剪版）

# 保留数据模型类（被 JSON 反射使用）
-keep class com.lanbing.smsforwarder.Channel { *; }
-keep class com.lanbing.smsforwarder.KeywordConfig { *; }
-keep class com.lanbing.smsforwarder.ChannelType { *; }
-keep class com.lanbing.smsforwarder.ForwardResult { *; }

# 保留 BroadcastReceiver 入口（系统反射调用）
-keep public class * extends android.content.BroadcastReceiver

# 保留 Service 入口（系统反射调用）
-keep public class * extends android.app.Service

# 保留 Application 入口
-keep public class * extends android.app.Application

# 保留 MainActivity（被 Manifest 引用）
-keep class com.lanbing.smsforwarder.MainActivity { *; }

# Compose 运行时反射需要的类（最小保留，让 R8 裁剪其余部分）
-dontwarn androidx.compose.**
-keep class androidx.compose.runtime.** { *; }
-keep @androidx.compose.runtime.Composable class * { *; }
-keep class androidx.compose.runtime.snapshots.** { *; }

# OkHttp / Okio（网络库，保留公共 API）
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# Kotlin 元数据（Compose 编译器需要）
-keep class kotlin.Metadata { *; }
-keepclassmembers class * {
    @kotlin.Metadata *;
}

# 保留 R 文件（资源引用）
-keep class **.R { *; }
-keep class **.R$* { *; }

# 移除所有日志调用，进一步减小体积
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
}
