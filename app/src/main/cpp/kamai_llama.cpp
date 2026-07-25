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
#include <cstring>
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

    // The context parameters, kept so the context (and its KV cache) can be
    // freed under memory pressure and recreated later without reloading the
    // memory-mapped model weights. Two-stage pressure response.
    int n_ctx           = 0;
    int n_threads       = 0;
    // Prefill (prompt ingestion) is compute-bound and parallelises across cores,
    // unlike the memory-bandwidth-bound decode, so it gets its own, larger count.
    int n_threads_batch = 0;

    // Position of the next token in the sequence.
    int n_past = 0;

    // The exact token sequence currently held in the KV cache (prompt tokens plus
    // any generated so far). On the next turn the new prompt is diffed against
    // this, and only the divergent suffix is decoded, so a long conversation does
    // not re-prefill its whole history every turn. See DECISIONS.md (#38).
    std::vector<llama_token> cached_tokens;

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

// llama.cpp's own log, forwarded to logcat under its own tag. What the loader
// decides is otherwise invisible on a phone: whether weight repacking engaged,
// whether flash attention is on, how the sliding-window cache was sized. Those
// are exactly the things that must be read rather than assumed (issues #49 and
// #51). Debug-level lines are dropped, since they are per-tensor and enormous.
//   adb logcat -s KamAI-llama
void llama_log_to_logcat(ggml_log_level level, const char * text, void *) {
    if (text == nullptr) return;
    int priority = ANDROID_LOG_INFO;
    switch (level) {
        case GGML_LOG_LEVEL_ERROR: priority = ANDROID_LOG_ERROR; break;
        case GGML_LOG_LEVEL_WARN:  priority = ANDROID_LOG_WARN;  break;
        case GGML_LOG_LEVEL_DEBUG: return;
        default: break;
    }
    __android_log_print(priority, "KamAI-llama", "%s", text);
}

std::string jstring_to_utf8(JNIEnv * env, jstring s) {
    if (s == nullptr) return {};
    const char * chars = env->GetStringUTFChars(s, nullptr);
    std::string out(chars ? chars : "");
    if (chars) env->ReleaseStringUTFChars(s, chars);
    return out;
}

// Builds a context (and its KV cache) for the already-loaded model, using the
// stored parameters. Returns true on success. Caller holds the mutex. This lets
// the context be freed under memory pressure and rebuilt later without touching
// the memory-mapped model weights.
bool build_context_locked() {
    if (g_session.model == nullptr) return false;
    if (g_session.ctx != nullptr) return true;

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx           = (uint32_t) g_session.n_ctx;
    cparams.n_batch         = 512;
    cparams.n_ubatch        = 512;
    cparams.n_threads       = g_session.n_threads;
    cparams.n_threads_batch = g_session.n_threads_batch > 0
        ? g_session.n_threads_batch : g_session.n_threads;
    cparams.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_AUTO;
    // Gemma interleaves local sliding-window attention with periodic global
    // attention. With a sliding-window KV cache, cells that fall behind the
    // window are overwritten, so trimming the tail of the sequence and decoding
    // again from that point leaves a hole the model reads as garbage: it loses
    // the turn structure and starts improvising delimiters into its answer.
    // That is issue #49, and it is why it only showed up in long conversations.
    // The prefix reuse in nativeIngest (issue #38) depends on the cache holding
    // exactly what cached_tokens says it holds, so the SWA cache is given the
    // full context size. It costs KV memory and buys a correct cache.
    cparams.swa_full = true;

    // The KV cache is quantised to q8_0, and flash attention is asked for
    // explicitly rather than left on AUTO (issue #53).
    //
    // Measured on this phone before the change: 336 MiB of KV at f16, 96 for the
    // non-SWA cache and 240 for the SWA one, and the SWA cache is that large
    // precisely because swa_full above buys cache correctness with memory. q8_0
    // halves both. That matters twice over: it gives the memory back, and decode
    // on a phone is bandwidth-bound, so a smaller cache is a faster one.
    //
    // Flash attention is a prerequisite for quantised KV in llama.cpp, so the two
    // go in together or not at all.
    //
    // Kept only if it wins, per the rule on #51, and it is measured below in
    // DECISIONS. The fallback is not decoration: if a future model or build
    // cannot do flash attention, AUTO with f16 is exactly today's behaviour, so
    // the app degrades to something known rather than failing to open a context.
    cparams.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_ENABLED;
    cparams.type_k = GGML_TYPE_Q8_0;
    cparams.type_v = GGML_TYPE_Q8_0;

    g_session.ctx = llama_init_from_model(g_session.model, cparams);
    if (g_session.ctx == nullptr) {
        LOGE("context with flash attention and q8_0 KV failed; falling back to f16");
        cparams.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_AUTO;
        cparams.type_k = GGML_TYPE_F16;
        cparams.type_v = GGML_TYPE_F16;
        g_session.ctx = llama_init_from_model(g_session.model, cparams);
    }
    if (g_session.ctx == nullptr) return false;
    llama_set_abort_callback(g_session.ctx, abort_callback, &g_session.abort);
    g_session.n_past = 0;
    // A freshly built context has an empty KV cache, so no tokens are reusable.
    g_session.cached_tokens.clear();
    return true;
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
    std::call_once(g_backend_once, []() {
        llama_log_set(llama_log_to_logcat, nullptr);
        llama_backend_init();
    });
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
        jstring jpath, jint n_ctx, jint n_threads, jint n_threads_batch, jint n_gpu_layers) {

    std::call_once(g_backend_once, []() {
        llama_log_set(llama_log_to_logcat, nullptr);
        llama_backend_init();
    });
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

    // What the CPU backend actually decided to use, rather than what the build
    // flags asked for. Issue #51 wants dot-product support and weight repacking
    // confirmed rather than assumed, and there is a documented case of repacking
    // silently not engaging. The repack buffer size already shows in the load
    // log; this is the other half, and it costs one line at load.
    LOGI("system: %s", llama_print_system_info());

    g_session.model     = model;
    g_session.ctx       = nullptr;
    g_session.n_ctx           = n_ctx;
    g_session.n_threads       = n_threads;
    g_session.n_threads_batch = n_threads_batch;
    g_session.n_past    = 0;
    g_session.abort.store(false);

    if (!build_context_locked()) {
        llama_model_free(model);
        g_session.model = nullptr;
        LOGE("failed to create context");
        return env->NewStringUTF(
            "There was not enough free memory to start this model. Close some apps, or pick a smaller model in Settings.");
    }

    LOGI("model loaded: n_ctx=%d threads=%d", n_ctx, n_threads);
    return env->NewStringUTF("");
}

// Frees the context and its KV cache but keeps the memory-mapped model resident.
// The moderate-pressure response: the conversation can continue, the next reply
// is a little slower while the context is rebuilt, and no model reload is needed.
JNIEXPORT void JNICALL
Java_com_kamsiob_kamai_llm_LlamaBridge_nativeReleaseContext(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(g_session.mu);
    g_session.abort.store(true);
    if (g_session.sampler) { llama_sampler_free(g_session.sampler); g_session.sampler = nullptr; }
    if (g_session.ctx)     { llama_free(g_session.ctx);             g_session.ctx     = nullptr; }
    g_session.n_past = 0;
    g_session.abort.store(false);
}

// Rebuilds the context if the model is loaded but the context was released.
// Returns an empty string on success, or a plain reason on failure.
JNIEXPORT jstring JNICALL
Java_com_kamsiob_kamai_llm_LlamaBridge_nativeEnsureContext(JNIEnv * env, jobject) {
    std::lock_guard<std::mutex> lock(g_session.mu);
    if (g_session.model == nullptr) {
        return env->NewStringUTF("No model is loaded.");
    }
    if (g_session.ctx != nullptr) return env->NewStringUTF("");
    if (build_context_locked()) return env->NewStringUTF("");
    return env->NewStringUTF(
        "There was not enough free memory to continue. Close some apps and try again.");
}

// True when the model weights are resident, regardless of whether the context
// (KV cache) is currently allocated.
JNIEXPORT jboolean JNICALL
Java_com_kamsiob_kamai_llm_LlamaBridge_nativeIsModelLoaded(JNIEnv *, jobject) {
    return g_session.model != nullptr ? JNI_TRUE : JNI_FALSE;
}

// True when the context (KV cache) is currently allocated.
JNIEXPORT jboolean JNICALL
Java_com_kamsiob_kamai_llm_LlamaBridge_nativeIsContextLoaded(JNIEnv *, jobject) {
    return g_session.ctx != nullptr ? JNI_TRUE : JNI_FALSE;
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
    if (n_tokens >= n_ctx) {
        return -3; // Caller turns this into the plain over-length message.
    }

    // Reuse the longest prefix already sitting in the KV cache. A normal next
    // turn is the same system prompt and history with one new message appended,
    // so the common prefix is almost everything and only the new turn is decoded.
    int prefix = 0;
    const int cached = (int) g_session.cached_tokens.size();
    const int maxp = std::min(cached, n_tokens);
    while (prefix < maxp && g_session.cached_tokens[prefix] == tokens[prefix]) prefix++;
    // Always leave at least one token to decode, so the model has a fresh position
    // to generate the next token from even when the prompt is unchanged.
    if (prefix >= n_tokens) prefix = n_tokens - 1;

    // Drop the KV entries after the common prefix and trim our record to match.
    //
    // The removal can genuinely fail: llama_memory_seq_rm returns false when a
    // partial sequence cannot be removed. If that is ignored, the cache still
    // holds the old tail while cached_tokens and n_past say it does not, and
    // every following token is generated against a context that is quietly
    // wrong. That is worse than being slow, so a failure here throws the whole
    // sequence away and re-prefills honestly. See issue #49.
    llama_memory_t mem = llama_get_memory(g_session.ctx);
    if (!llama_memory_seq_rm(mem, 0, prefix, -1)) {
        LOGE("seq_rm(%d, -1) refused; clearing the sequence and re-prefilling", prefix);
        llama_memory_clear(mem, true);
        prefix = 0;
    }
    g_session.n_past = prefix;
    g_session.cached_tokens.resize(prefix);

    g_session.abort.store(false);

    // Feed the divergent suffix in batches so a long prompt does not exceed n_batch.
    const int n_batch = (int) llama_n_batch(g_session.ctx);
    for (int i = prefix; i < n_tokens; i += n_batch) {
        const int chunk = std::min(n_batch, n_tokens - i);
        llama_batch batch = llama_batch_get_one(tokens.data() + i, chunk);
        const int rc = llama_decode(g_session.ctx, batch);
        if (rc != 0) {
            // Whatever was processed before this stopped stays in the cache, so
            // the bookkeeping no longer matches it. Clear both rather than leave
            // a lie behind for the next turn to reuse. How much of the batch got
            // through is not knowable from here, so clearing is the only honest
            // option even though it costs the prefix.
            llama_memory_clear(mem, true);
            g_session.n_past = 0;
            g_session.cached_tokens.clear();

            // rc == 2 is llama.cpp's "aborted", which here means the user pressed
            // stop while the prompt was still being read in. That is not a
            // failure and must not be reported as one: the caller turns this into
            // an ordinary user stop. Anything else genuinely went wrong.
            if (rc == 2) {
                LOGI("ingest aborted by request; sequence cleared");
                return -5;
            }
            LOGE("llama_decode failed during ingest: %d; clearing the sequence", rc);
            return -4;
        }
        g_session.n_past += chunk;
        for (int k = 0; k < chunk; k++) g_session.cached_tokens.push_back(tokens[i + k]);
    }

    // Cheap insurance against the failure mode this file most needs to avoid:
    // cached_tokens drifting out of step with the cache means the model answers
    // from the wrong history, silently. Positions are zero based, so the last
    // position plus one is the number of tokens held.
    const llama_pos held = llama_memory_seq_pos_max(mem, 0);
    if (held + 1 != (int) g_session.cached_tokens.size()) {
        LOGE("cache desync: memory holds %d tokens, record says %d; clearing",
             (int) held + 1, (int) g_session.cached_tokens.size());
        llama_memory_clear(mem, true);
        g_session.n_past = 0;
        g_session.cached_tokens.clear();
        return -4;
    }

    // The count actually processed this turn (the new work), so the perf log and
    // the caller reflect real cost rather than the whole prompt length.
    return n_tokens - prefix;
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
    // Record the generated token too, so it is part of the history the next turn
    // can reuse rather than re-prefill.
    g_session.cached_tokens.push_back(token);

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
    g_session.cached_tokens.clear();
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

// ---------------------------------------------------------------------------
// Conversation state, saved and restored across app sessions (issue #52).
//
// Prefix reuse (#38) already means an ongoing conversation does not re-prefill
// its history every turn, but it lives entirely in the context, so closing the
// app throws it away: reopening a long chat re-reads every token before the
// first new one can be produced.
//
// The blob is the sequence's KV state plus the token list that describes it,
// because the two are useless apart. Without the tokens the next turn cannot
// diff its prompt against what is cached and would have to reset anyway; with
// them, a restored conversation continues exactly as if the app had never been
// closed.
//
// Layout, little-endian, which is every Android device this ships to:
//   int32   token count
//   int32 * token count   the cached tokens
//   bytes                 llama_state_seq_get_data for sequence 0
//
// Nothing here writes a file. The bytes go back to Kotlin, which encrypts them
// with the same Keystore-wrapped key as the database before they touch disk: a
// serialised KV state is the conversation in reconstructible form, and a
// plaintext copy of it beside an encrypted database would quietly undo the
// encryption. See DECISIONS.md.
// ---------------------------------------------------------------------------

JNIEXPORT jbyteArray JNICALL
Java_com_kamsiob_kamai_llm_LlamaBridge_nativeSaveState(JNIEnv * env, jobject) {
    std::lock_guard<std::mutex> lock(g_session.mu);
    if (g_session.ctx == nullptr) return nullptr;
    if (g_session.cached_tokens.empty()) return nullptr;

    const size_t n_tokens = g_session.cached_tokens.size();
    const size_t header   = sizeof(int32_t) * (1 + n_tokens);
    const size_t seq_size = llama_state_seq_get_size(g_session.ctx, 0);
    if (seq_size == 0) return nullptr;

    std::vector<uint8_t> buffer(header + seq_size);

    int32_t count = (int32_t) n_tokens;
    std::memcpy(buffer.data(), &count, sizeof(int32_t));
    for (size_t i = 0; i < n_tokens; ++i) {
        int32_t token = (int32_t) g_session.cached_tokens[i];
        std::memcpy(buffer.data() + sizeof(int32_t) * (1 + i), &token, sizeof(int32_t));
    }

    const size_t written = llama_state_seq_get_data(
        g_session.ctx, buffer.data() + header, seq_size, 0);
    if (written == 0) {
        LOGE("state save: llama_state_seq_get_data returned 0");
        return nullptr;
    }

    const size_t total = header + written;
    jbyteArray out = env->NewByteArray((jsize) total);
    if (out == nullptr) return nullptr;
    env->SetByteArrayRegion(out, 0, (jsize) total,
                            reinterpret_cast<const jbyte *>(buffer.data()));
    LOGI("state save: %zu tokens, %zu bytes", n_tokens, total);
    return out;
}

// Restores a blob written by nativeSaveState. Returns the number of tokens now
// cached, or a negative value if nothing was restored and the context is
// unchanged.
//
// A blob from a different model is not detectable here, so the caller keys its
// files by model. What is detectable is a truncated or corrupt blob, and a
// refusal leaves the context exactly as it was rather than half-filled: a
// partially restored cache would produce a prompt diff against tokens that are
// not really there, which is silently wrong output rather than a slow turn.
JNIEXPORT jint JNICALL
Java_com_kamsiob_kamai_llm_LlamaBridge_nativeRestoreState(
        JNIEnv * env, jobject, jbyteArray jblob) {

    std::lock_guard<std::mutex> lock(g_session.mu);
    if (g_session.ctx == nullptr || jblob == nullptr) return -1;

    const jsize size = env->GetArrayLength(jblob);
    if (size < (jsize) sizeof(int32_t)) return -1;

    std::vector<uint8_t> buffer((size_t) size);
    env->GetByteArrayRegion(jblob, 0, size, reinterpret_cast<jbyte *>(buffer.data()));

    int32_t count = 0;
    std::memcpy(&count, buffer.data(), sizeof(int32_t));
    if (count <= 0 || count > g_session.n_ctx) return -1;

    const size_t header = sizeof(int32_t) * (1 + (size_t) count);
    if ((size_t) size <= header) return -1;

    std::vector<llama_token> tokens((size_t) count);
    for (int32_t i = 0; i < count; ++i) {
        int32_t token = 0;
        std::memcpy(&token, buffer.data() + sizeof(int32_t) * (1 + (size_t) i), sizeof(int32_t));
        tokens[(size_t) i] = (llama_token) token;
    }

    // Clear first: set_data writes into the sequence, and leaving whatever was
    // there underneath it would mix two conversations.
    llama_memory_clear(llama_get_memory(g_session.ctx), true);

    const size_t read = llama_state_seq_set_data(
        g_session.ctx, buffer.data() + header, (size_t) size - header, 0);
    if (read == 0) {
        LOGE("state restore: rejected by llama_state_seq_set_data");
        llama_memory_clear(llama_get_memory(g_session.ctx), true);
        g_session.cached_tokens.clear();
        g_session.n_past = 0;
        return -1;
    }

    g_session.cached_tokens = std::move(tokens);
    g_session.n_past = count;
    LOGI("state restore: %d tokens", count);
    return count;
}

} // extern "C"
