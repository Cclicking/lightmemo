# Keep annotations used by kotlinx.serialization / navigation.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep serializers generated for @Serializable models in this app.
-keep,includedescriptorclasses class com.click.lightmemo.**$$serializer { *; }
-keepclassmembers class com.click.lightmemo.** {
    *** Companion;
}
-keepclasseswithmembers class com.click.lightmemo.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# kotlinx.serialization plugin metadata
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
