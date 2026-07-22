// JNI bridge between Kam AI's Kotlin layer and llama.cpp.
//
// The bridge is deliberately thin. It owns three handles (model, context,
// sampler), exposes a load / tokenize / decode / sample / free cycle, and lets
// Kotlin drive the generation loop one token at a time so that streaming,
// stopping, and thermal backoff are all decided in Kotlin where the rest of the
// app's logic lives.

#include <jni.h>
#include <android/log.h>

#include <string>
#include <vector>
#include <mutex>
#include <atomic>

#include "llama.h"

#define TAG "KamAI-native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace {

struct Session {
    llama_model   * model   = nullptr;
    llama_context * ctx     = nullptr;
    llama_sampler * sampler = nullptr;

    // Position of the next token in the sequence.
    int n_past = 0;

    // Set from Kotlin to interrupt a decode that is already running.
    std::atomic<bool> abort{false};

    std::mutex mu;
};

// One session at a time. Running two models on a phone is not a thing this app
// will ever do, and a single global keeps the lifetime rules obvious.
Session g_session;
std::once_flag g_backend_once;

bool abort_callback(void * data) {
    auto * flag = static_cast<std::atomic<bool> *>(data);
    return flag != nullptr && flag->load();
}

std::string jstring_to_utf8(JNIEnv * env, jstring s) {
    if (s == nullptr) return {};
    const char * chars = env->GetStringUTFChars(s, nullptr);
    std::string out(chars ? chars : "");
    if (chars) env->ReleaseStringUTFChars(s, chars);
    return out;
}

// Turns one token into text. Called for every token generated, so it keeps a
// small stack buffer for the common case and only heap-allocates for the rare
// long piece.
std::string token_to_text(const llama_vocab * vocab, llama_token token) {
    char buf[128];
    int n = llama_token_to_piece(vocab, token, buf, sizeof(buf), 0, /*special=*/false);
    if (n >= 0) {
        return std::string(buf, n);
    }
    std::vector<char> big(-n);
    n = llama_token_to_piece(vocab, token, big.data(), (int) big.size(), 0, false);
    if (n < 0) return {};
    return std::string(big.data(), n);
}

} // namespace

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_kamsiob_kamai_llm_LlamaBridge_nativeSystemInfo(JNIEnv * env, jobject) {
    std::call_once(g_backend_once, []() { llama_backend_init(); });
    std::string info;
    info += "llama.cpp bridge ready";
    info += ", mmap=";  info += llama_supports_mmap()  ? "yes" : "no";
    info += ", mlock="; info += llama_supports_mlock() ? "yes" : "no";
    info += ", gpu=";   info += llama_supports_gpu_offload() ? "yes" : "no";
    return env->NewStringUTF(info.c_str());
}

// Loads a GGUF file and builds a context. Returns an empty string on success or
// a plain-language reason on failure, which the Kotlin layer shows as-is.
JNIEXPORT jstring JNICALL
Java_com_kamsiob_kamai_llm_LlamaBridge_nativeLoad(
        JNIEnv * env, jobject,
        jstring jpath, jint n_ctx, jint n_threads, jint n_gpu_layers) {

    std::call_once(g_backend_once, []() { llama_backend_init(); });
    std::lock_guard<std::mutex> lock(g_session.mu);

    if (g_session.ctx != nullptr) {
        return env->NewStringUTF("A model is already loaded. Unload it first.");
    }

    const std::string path = jstring_to_utf8(env, jpath);

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = n_gpu_layers;
    mparams.use_mmap     = true;
    mparams.use_mlock    = false;

    llama_model * model = llama_model_load_from_file(path.c_str(), mparams);
    if (model == nullptr) {
        LOGE("failed to load model at %s", path.c_str());
        return env->NewStringUTF(
            "That model file could not be opened. It may be incomplete, so try downloading it again.");
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx         = (uint32_t) n_ctx;
    cparams.n_batch       = 512;
    cparams.n_ubatch      = 512;
    cparams.n_threads     = n_threads;
    cparams.n_threads_batch = n_threads;
    cparams.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_AUTO;

    llama_context * ctx = llama_init_from_model(model, cparams);
    if (ctx == nullptr) {
        llama_model_free(model);
        LOGE("failed to create context");
        return env->NewStringUTF(
            "There was not enough free memory to start this model. Close some apps, or pick a smaller model in Settings.");
    }

    llama_set_abort_callback(ctx, abort_callback, &g_session.abort);

    g_session.model  = model;
    g_session.ctx    = ctx;
    g_session.n_past = 0;
    g_session.abort.store(false);

    LOGI("model loaded: n_ctx=%d threads=%d", n_ctx, n_threads);
    return env->NewStringUTF("");
}

// Builds the sampler chain. Kam AI never exposes these values in the UI, so
// they are set once per generation from fixed per-mode constants in Kotlin.
JNIEXPORT void JNICALL
Java_com_kamsiob_kamai_llm_LlamaBridge_nativeConfigureSampler(
        JNIEnv *, jobject,
        jfloat temperature, jfloat top_p, jfloat min_p,
        jint top_k, jfloat repeat_penalty, jint repeat_last_n, jint seed) {

    std::lock_guard<std::mutex> lock(g_session.mu);
    if (g_session.sampler != nullptr) {
        llama_sampler_free(g_session.sampler);
        g_session.sampler = nullptr;
    }

    auto sparams = llama_sampler_chain_default_params();
    sparams.no_perf = true;
    llama_sampler * chain = llama_sampler_chain_init(sparams);

    if (repeat_penalty > 1.0f) {
        llama_sampler_chain_add(chain,
            llama_sampler_init_penalties(repeat_last_n, repeat_penalty, 0.0f, 0.0f));
    }
    if (top_k > 0) {
        llama_sampler_chain_add(chain, llama_sampler_init_top_k(top_k));
    }
    if (top_p < 1.0f) {
        llama_sampler_chain_add(chain, llama_sampler_init_top_p(top_p, 1));
    }
    if (min_p > 0.0f) {
        llama_sampler_chain_add(chain, llama_sampler_init_min_p(min_p, 1));
    }
    llama_sampler_chain_add(chain, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(chain, llama_sampler_init_dist((uint32_t) seed));

    g_session.sampler = chain;
}

// Tokenizes text and feeds it through the model, extending the sequence.
// Returns the number of tokens consumed, or a negative value on failure.
JNIEXPORT jint JNICALL
Java_com_kamsiob_kamai_llm_LlamaBridge_nativeIngest(
        JNIEnv * env, jobject, jstring jtext, jboolean add_special) {

    std::lock_guard<std::mutex> lock(g_session.mu);
    if (g_session.ctx == nullptr) return -1;

    const std::string text = jstring_to_utf8(env, jtext);
    const llama_vocab * vocab = llama_model_get_vocab(g_session.model);

    // Negative return is the required buffer size.
    int n_needed = -llama_tokenize(vocab, text.c_str(), (int) text.size(),
                                   nullptr, 0, add_special, /*parse_special=*/true);
    if (n_needed <= 0) return 0;

    std::vector<llama_token> tokens(n_needed);
    int n_tokens = llama_tokenize(vocab, text.c_str(), (int) text.size(),
                                  tokens.data(), (int) tokens.size(),
                                  add_special, /*parse_special=*/true);
    if (n_tokens < 0) return -2;
    tokens.resize(n_tokens);

    const int n_ctx = (int) llama_n_ctx(g_session.ctx);
    if (g_session.n_past + n_tokens >= n_ctx) {
        return -3; // Caller turns this into the plain over-length message.
    }

    g_session.abort.store(false);

    // Feed in batches so a long prompt does not exceed n_batch.
    const int n_batch = (int) llama_n_batch(g_session.ctx);
    for (int i = 0; i < n_tokens; i += n_batch) {
        const int chunk = std::min(n_batch, n_tokens - i);
        llama_batch batch = llama_batch_get_one(tokens.data() + i, chunk);
        const int rc = llama_decode(g_session.ctx, batch);
        if (rc != 0) {
            LOGE("llama_decode failed during ingest: %d", rc);
            return -4;
        }
        g_session.n_past += chunk;
    }

    return n_tokens;
}

// Samples exactly one token and decodes it back into the context. Returns the
// piece of text, or null once the model emits an end-of-generation token.
JNIEXPORT jstring JNICALL
Java_com_kamsiob_kamai_llm_LlamaBridge_nativeNextToken(JNIEnv * env, jobject) {
    std::lock_guard<std::mutex> lock(g_session.mu);
    if (g_session.ctx == nullptr || g_session.sampler == nullptr) return nullptr;
    if (g_session.abort.load()) return nullptr;

    const llama_vocab * vocab = llama_model_get_vocab(g_session.model);

    llama_token token = llama_sampler_sample(g_session.sampler, g_session.ctx, -1);
    if (llama_vocab_is_eog(vocab, token)) {
        return nullptr;
    }

    llama_sampler_accept(g_session.sampler, token);

    const int n_ctx = (int) llama_n_ctx(g_session.ctx);
    if (g_session.n_past + 1 >= n_ctx) {
        return nullptr; // Out of room. Kotlin reports this plainly.
    }

    const std::string piece = token_to_text(vocab, token);

    llama_batch batch = llama_batch_get_one(&token, 1);
    if (llama_decode(g_session.ctx, batch) != 0) {
        return nullptr;
    }
    g_session.n_past += 1;

    return env->NewStringUTF(piece.c_str());
}

JNIEXPORT void JNICALL
Java_com_kamsiob_kamai_llm_LlamaBridge_nativeRequestStop(JNIEnv *, jobject) {
    g_session.abort.store(true);
}

// Drops the whole sequence so the next turn starts from a clean context.
JNIEXPORT void JNICALL
Java_com_kamsiob_kamai_llm_LlamaBridge_nativeResetContext(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(g_session.mu);
    if (g_session.ctx == nullptr) return;
    llama_memory_clear(llama_get_memory(g_session.ctx), true);
    g_session.n_past = 0;
    g_session.abort.store(false);
}

JNIEXPORT jint JNICALL
Java_com_kamsiob_kamai_llm_LlamaBridge_nativeContextUsed(JNIEnv *, jobject) {
    return g_session.n_past;
}

JNIEXPORT jint JNICALL
Java_com_kamsiob_kamai_llm_LlamaBridge_nativeContextSize(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(g_session.mu);
    if (g_session.ctx == nullptr) return 0;
    return (jint) llama_n_ctx(g_session.ctx);
}

// Counts tokens without touching the context. Used for context budgeting when
// deciding how much memory and history to inject.
JNIEXPORT jint JNICALL
Java_com_kamsiob_kamai_llm_LlamaBridge_nativeCountTokens(JNIEnv * env, jobject, jstring jtext) {
    std::lock_guard<std::mutex> lock(g_session.mu);
    if (g_session.model == nullptr) return -1;

    const std::string text = jstring_to_utf8(env, jtext);
    const llama_vocab * vocab = llama_model_get_vocab(g_session.model);
    return -llama_tokenize(vocab, text.c_str(), (int) text.size(),
                           nullptr, 0, false, true);
}

// The model's own chat template, so prompts are formatted the way the model was
// trained rather than with a guessed format.
JNIEXPORT jstring JNICALL
Java_com_kamsiob_kamai_llm_LlamaBridge_nativeChatTemplate(JNIEnv * env, jobject) {
    std::lock_guard<std::mutex> lock(g_session.mu);
    if (g_session.model == nullptr) return nullptr;
    const char * tmpl = llama_model_chat_template(g_session.model, nullptr);
    return tmpl ? env->NewStringUTF(tmpl) : nullptr;
}

JNIEXPORT void JNICALL
Java_com_kamsiob_kamai_llm_LlamaBridge_nativeUnload(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(g_session.mu);
    g_session.abort.store(true);

    if (g_session.sampler) { llama_sampler_free(g_session.sampler); g_session.sampler = nullptr; }
    if (g_session.ctx)     { llama_free(g_session.ctx);             g_session.ctx     = nullptr; }
    if (g_session.model)   { llama_model_free(g_session.model);     g_session.model   = nullptr; }
    g_session.n_past = 0;
}

JNIEXPORT jboolean JNICALL
Java_com_kamsiob_kamai_llm_LlamaBridge_nativeIsLoaded(JNIEnv *, jobject) {
    return g_session.ctx != nullptr ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
