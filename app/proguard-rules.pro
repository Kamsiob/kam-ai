# The JNI bridge is called by name from native code, so its methods must not be
# renamed or stripped.
-keepclasseswithmembernames,includedescriptorclasses class com.kamsiob.kamai.llm.LlamaBridge {
    native <methods>;
}
-keep class com.kamsiob.kamai.llm.LlamaBridge { *; }

# Room generates implementations that are looked up reflectively.
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# Keep line numbers so a stack trace someone pastes into an issue is readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
