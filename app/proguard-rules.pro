# Smali Helper Plus R8 rules. Most libraries ship consumer rules; keep this file minimal.
-keepattributes *Annotation*, InnerClasses, Signature
-dontnote kotlinx.serialization.AnnotationsKt

# @Serializable content DTOs, backup files and type-safe navigation routes.
-keepclassmembers @kotlinx.serialization.Serializable class com.riyadm.smalihelperplus.** {
    *** Companion;
    public static ** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.riyadm.smalihelperplus.** {
    kotlinx.serialization.KSerializer serializer(...);
}
