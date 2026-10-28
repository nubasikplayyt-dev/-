# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keep,includedescriptorclasses class app.lingvo.**$$serializer { *; }
-keepclassmembers class app.lingvo.** { *** Companion; }
-keepclasseswithmembers class app.lingvo.** { kotlinx.serialization.KSerializer serializer(...); }
