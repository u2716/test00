# JNI-facing dictionary classes — see hoshidicts_jni.cpp.
-keep class chimahon.** { *; }
-keepclassmembers class chimahon.** { *; }

-keepattributes Signature
-keepattributes *Annotation*

# MNN OCR JNI keep rules. The native layer resolves
# chimahon.local.ocr.mnn.MnnInterpreter by hardcoded name; R8 must not
# rename or strip it.
-keep class chimahon.local.ocr.mnn.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
