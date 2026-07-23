// JNI bridge for whisper.cpp speech to text.
//
// This is deliberately small: it loads a whisper model, transcribes a buffer of
// 16 kHz mono float PCM, and frees. The recording, resampling, and any streaming
// live in Kotlin, alongside the rest of the app's behaviour, exactly as the
// llama.cpp bridge keeps its generation loop above the native layer.
//
// It is built into its own shared library (libkamwhisper.so) with its own copy
// of ggml, kept apart from libkamai.so. All symbols but the JNI entry points are
// hidden, so the two ggml copies never collide.

#include <jni.h>
#include <android/log.h>

#include <mutex>
#include <string>
#include <vector>

#include "whisper.h"

#define LOG_TAG "kamwhisper"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace {

std::mutex g_mutex;
whisper_context * g_ctx = nullptr;

std::string jstr(JNIEnv * env, jstring s) {
    if (s == nullptr) return {};
    const char * c = env->GetStringUTFChars(s, nullptr);
    std::string out = c ? c : "";
    if (c) env->ReleaseStringUTFChars(s, c);
    return out;
}

} // namespace

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_kamsiob_kamai_voice_WhisperBridge_nativeLoad(
        JNIEnv * env, jobject, jstring jpath) {
    std::lock_guard<std::mutex> lock(g_mutex);

    if (g_ctx != nullptr) {
        whisper_free(g_ctx);
        g_ctx = nullptr;
    }

    const std::string path = jstr(env, jpath);

    whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false; // CPU only, the reliable path, matching the LLM.

    g_ctx = whisper_init_from_file_with_params(path.c_str(), cparams);
    if (g_ctx == nullptr) {
        return env->NewStringUTF(
            "That voice model could not be loaded. Try downloading it again.");
    }
    return env->NewStringUTF("");
}

JNIEXPORT jboolean JNICALL
Java_com_kamsiob_kamai_voice_WhisperBridge_nativeIsLoaded(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    return g_ctx != nullptr ? JNI_TRUE : JNI_FALSE;
}

// Transcribes 16 kHz mono float PCM. Returns the text, or an empty string.
JNIEXPORT jstring JNICALL
Java_com_kamsiob_kamai_voice_WhisperBridge_nativeTranscribe(
        JNIEnv * env, jobject, jfloatArray jpcm, jint nThreads, jstring jlang) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_ctx == nullptr) return env->NewStringUTF("");

    const jsize n = env->GetArrayLength(jpcm);
    if (n <= 0) return env->NewStringUTF("");

    std::vector<float> pcm(static_cast<size_t>(n));
    env->GetFloatArrayRegion(jpcm, 0, n, pcm.data());

    const std::string lang = jstr(env, jlang);

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.n_threads        = nThreads > 0 ? nThreads : 4;
    params.translate        = false;
    params.no_timestamps    = true;
    params.print_progress   = false;
    params.print_realtime   = false;
    params.print_special    = false;
    params.print_timestamps = false;
    params.single_segment   = false;
    params.language         = lang.empty() ? "en" : lang.c_str();

    if (whisper_full(g_ctx, params, pcm.data(), static_cast<int>(pcm.size())) != 0) {
        LOGE("whisper_full failed");
        return env->NewStringUTF("");
    }

    std::string text;
    const int segments = whisper_full_n_segments(g_ctx);
    for (int i = 0; i < segments; ++i) {
        const char * seg = whisper_full_get_segment_text(g_ctx, i);
        if (seg) text += seg;
    }
    return env->NewStringUTF(text.c_str());
}

JNIEXPORT void JNICALL
Java_com_kamsiob_kamai_voice_WhisperBridge_nativeFree(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_ctx != nullptr) {
        whisper_free(g_ctx);
        g_ctx = nullptr;
    }
}

} // extern "C"
