# YoutubeDL-Android rules
-keep class com.yausername.youtubedl_android.** { *; }

# WorkManager 通过反射创建 Room 生成的数据库实现，必须保留无参构造函数。
-keep class androidx.work.impl.WorkDatabase_Impl {
    <init>();
}

# 移除所有 Log 调用以减小体积并保护隐私
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# 进一步压缩混淆
-repackageclasses ''
-allowaccessmodification
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*
-mergeinterfacesaggressively
