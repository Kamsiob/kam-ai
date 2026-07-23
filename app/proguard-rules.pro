# The JNI bridges are called by name from native code, so their classes and
# native methods must never be renamed or stripped. This covers llama.cpp,
# whisper.cpp, and the sherpa-onnx text-to-speech runtime.
-keepclasseswithmembernames,includedescriptorclasses class com.kamsiob.kamai.llm.LlamaBridge {
    native <methods>;
}
-keep class com.kamsiob.kamai.llm.LlamaBridge { *; }

-keepclasseswithmembernames,includedescriptorclasses class com.kamsiob.kamai.voice.WhisperBridge {
    native <methods>;
}
-keep class com.kamsiob.kamai.voice.WhisperBridge { *; }

# sherpa-onnx: its Kotlin API is bound to libsherpa-onnx-jni.so by class and
# method name, and the config data classes cross the JNI boundary by field.
-keep class com.k2fsa.sherpa.onnx.** { *; }
-keepclasseswithmembernames class com.k2fsa.sherpa.onnx.** {
    native <methods>;
}

# Any remaining native methods anywhere keep their names.
-keepclasseswithmembernames class * {
    native <methods>;
}

# pdfbox-android loads resources and uses reflection internally.
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-dontwarn com.tom_roush.**

# SQLCipher's JNI layer.
-keep class net.zetetic.** { *; }
-dontwarn net.zetetic.**

# Room entities are referenced by the generated database code.
-keep class com.kamsiob.kamai.data.*Entity { *; }
-keep class com.kamsiob.kamai.data.SettingEntity { *; }

# Room generates implementations that are looked up reflectively.
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# Keep line numbers so a stack trace someone pastes into an issue is readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
