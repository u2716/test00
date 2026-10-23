# word_audio_jni.cpp resolves WordAudioDatabase and its nested LocalEntry
# class by exact name via FindClass / GetMethodID. Without these rules
# R8 renames the classes and the first nativeFindEntries() call aborts.
-keep class chimahon.audio.WordAudioDatabase { <init>(...); *; }
-keep class chimahon.audio.WordAudioDatabase$LocalEntry { <init>(...); <fields>; }
