# JNI-facing dictionary classes — see hoshidicts_jni.cpp.
-keep class chimahon.** { *; }
-keepclassmembers class chimahon.** { *; }

-keepattributes Signature
-keepattributes *Annotation*

# ONNX Runtime JNI keep rules (Meiki OCR, wired in M4). Without these
# ai.onnxruntime.TensorInfo and friends are renamed/stripped and the first
# OrtSession.run() aborts with "JNI DETECTED ERROR: mid == null".
-keep class ai.onnxruntime.** { *; }
-keepclassmembers class ai.onnxruntime.** { *; }
-keepclasseswithmembers class ai.onnxruntime.** { <init>(...); }
-dontwarn ai.onnxruntime.**
