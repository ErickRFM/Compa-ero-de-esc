# Keep kotlinx.serialization generated serializers.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# Ktor uses reflection for some engine internals.
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# Keep the shared contracts the API serialises.
-keep class org.companerodeescuela.shared.contracts.** { *; }

# PDFBox-Android optionally decodes JPEG-2000 via a JP2 library that is not
# bundled. Timetable text extraction never invokes this image-only decoder.
# Suppress only this optional missing class, not all PDFBox warnings.
-dontwarn com.gemalto.jp2.JP2Decoder
