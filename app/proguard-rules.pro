# 混淆规则（release 开启混淆时生效）
# 保留 Room、DataStore、Compose 等框架所需
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep class com.lifebench.app.data.** { *; }
-keepattributes *Annotation*
-dontwarn org.jetbrains.**
