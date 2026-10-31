#include <jni.h>
#include <android/log.h>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <cstdint>
#include <mutex>
#include <vector>

#include "vocr.h"
#include "vocr_lm.h"
#include "vocr_lines.h"
#include "vocr_lines_rows.h"
#include "vocr_charbox.h"
#include "vocr_textspot6.h"
#include "vocr_textspot7.h"
#include "vocr_vertical.h"

#define TAG "vocr-jni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static void* a16(size_t n) {
    void* p = nullptr;
    if (n == 0 || posix_memalign(&p, 16, n) != 0) return nullptr;
    return p;
}

static constexpr unsigned kMaxFrames = 129;
static constexpr unsigned kTopK      = 8;
static constexpr unsigned kBeam      = 8;
// Upper bound on the LM/beam text we hand back; the C decoder never exceeds this in practice.
static constexpr unsigned kTextCap   = 4096;

struct VocrHandle {
    void*      blob = nullptr; size_t blob_bytes = 0;
    vocr_model model{};
    vocr_ctx   ctx{};
    void*      arena = nullptr; size_t arena_bytes = 0;
    void*      spotter_blob = nullptr; size_t spotter_blob_bytes = 0;
    vocr_textspot6 spotter{};

    bool       has_lm = false;
    void*      lm_blob = nullptr; size_t lm_blob_bytes = 0;
    vocr_lm    lm{};
    vocr_lm_ctx lm_ctx{};
    void*      lm_ws = nullptr; size_t lm_ws_bytes = 0;

    float*    topv  = nullptr;
    uint16_t* topc  = nullptr;
    float*    blank = nullptr;
    uint16_t* best  = nullptr;
    uint32_t* prof  = nullptr;
    vocr_charbox* charbox = nullptr;
    int16_t*  align_map = nullptr;   // kTextCap entries

    char greedy[kTextCap]{};
    char lmtext[kTextCap]{};

    std::mutex lock;
};

static void release(VocrHandle* h) {
    if (!h) return;
    std::free(h->topv); std::free(h->topc); std::free(h->blank);
    std::free(h->best); std::free(h->prof); std::free(h->charbox);
    std::free(h->align_map);
    std::free(h->lm_ws); std::free(h->lm_blob);
    std::free(h->arena); std::free(h->blob);
    std::free(h->spotter_blob);
    delete h;
}

static bool read_file(const char* path, void** out, size_t* n, char* err, size_t errsz) {
    FILE* f = std::fopen(path, "rb");
    if (!f) { std::snprintf(err, errsz, "open(%s) failed", path); return false; }
    std::fseek(f, 0, SEEK_END); long sz = std::ftell(f); std::fseek(f, 0, SEEK_SET);
    if (sz <= 0) { std::fclose(f); std::snprintf(err, errsz, "%s is empty", path); return false; }
    *n = static_cast<size_t>(sz);
    *out = a16(*n);
    bool ok = *out && std::fread(*out, 1, *n, f) == *n;
    std::fclose(f);
    if (!ok) std::snprintf(err, errsz, "read(%s) failed", path);
    return ok;
}

extern "C" JNIEXPORT jlong JNICALL
Java_chimahon_local_ocr_VocrInterpreter_nativeCreate(
        JNIEnv* env, jclass, jstring jBlobPath, jstring jLmPath, jstring jSpotterPath, jint mode) {
    const char* cpath = env->GetStringUTFChars(jBlobPath, nullptr);
    VocrHandle* h = new VocrHandle();
    char err[256]{};

    if (!read_file(cpath, &h->blob, &h->blob_bytes, err, sizeof err)) {
        LOGE("%s", err); env->ReleaseStringUTFChars(jBlobPath, cpath); release(h); return 0;
    }
    env->ReleaseStringUTFChars(jBlobPath, cpath);

    int rc = vocr_check_crc(h->blob, h->blob_bytes);
    if (rc) { LOGE("vocr_check_crc: %d", rc); release(h); return 0; }
    rc = vocr_load(&h->model, h->blob, h->blob_bytes);
    if (rc) { LOGE("vocr_load: %d", rc); release(h); return 0; }

    vocr_opts opts = {};
    opts.mode = static_cast<int>(mode);
    h->arena_bytes = vocr_arena_bytes(&h->model, &opts);
    h->arena = a16(h->arena_bytes);
    if (!h->arena) { release(h); return 0; }
    rc = vocr_init(&h->ctx, &h->model, &opts, h->arena, h->arena_bytes);
    if (rc) { LOGE("vocr_init: %d", rc); release(h); return 0; }

    if (jSpotterPath != nullptr) {
        const char* sp = env->GetStringUTFChars(jSpotterPath, nullptr);
        if (read_file(sp, &h->spotter_blob, &h->spotter_blob_bytes, err, sizeof err)) {
            int src = vocr_textspot6_load(&h->spotter, h->spotter_blob, h->spotter_blob_bytes);
            if (src) { LOGE("vocr_textspot_load: %d", src); std::free(h->spotter_blob); h->spotter_blob = nullptr; }
        } else {
            LOGE("%s", err);
        }
        env->ReleaseStringUTFChars(jSpotterPath, sp);
    }

    if (jLmPath != nullptr) {
        const char* lp = env->GetStringUTFChars(jLmPath, nullptr);
        if (read_file(lp, &h->lm_blob, &h->lm_blob_bytes, err, sizeof err)) {
            int lrc = vocr_lm_check_crc(h->lm_blob, h->lm_blob_bytes);
            if (lrc) { LOGE("vocr_lm_check_crc: %d", lrc); std::free(h->lm_blob); h->lm_blob = nullptr; h->lm_blob_bytes = 0; }
            else {
                lrc = vocr_lm_load(&h->lm, h->lm_blob, h->lm_blob_bytes);
                if (lrc) { LOGE("vocr_lm_load: %d", lrc); std::free(h->lm_blob); h->lm_blob = nullptr; h->lm_blob_bytes = 0; }
            }
        } else { LOGE("%s", err); }
        env->ReleaseStringUTFChars(jLmPath, lp);

        if (h->lm_blob) {
            vocr_lm_opts lo = {};
            lo.beam = kBeam; lo.topk = kTopK;
            lo.alpha = 500; lo.beta = 3500; lo.gate = 8000;
            lo.flags = VOCR_LM_EOS; lo.bracket = 8000;
            lo.max_frames = kMaxFrames;
            h->lm_ws_bytes = vocr_lm_workspace_bytes(&h->model, &h->lm, &lo);
            h->lm_ws = a16(h->lm_ws_bytes);
            if (h->lm_ws) {
                int lrc = vocr_lm_init(&h->lm_ctx, &h->model, &h->lm, &lo, h->lm_ws, h->lm_ws_bytes);
                h->has_lm = (lrc == 0);
                if (!h->has_lm) { LOGE("vocr_lm_init: %d", lrc); std::free(h->lm_ws); h->lm_ws = nullptr; }
            }
        }
    }

    h->topv      = static_cast<float*>(a16(kMaxFrames * kTopK * sizeof(float)));
    h->topc      = static_cast<uint16_t*>(a16(kMaxFrames * kTopK * sizeof(uint16_t)));
    h->blank     = static_cast<float*>(a16(kMaxFrames * sizeof(float)));
    h->best      = static_cast<uint16_t*>(a16(kMaxFrames * sizeof(uint16_t)));
    h->prof      = static_cast<uint32_t*>(a16(1024 * sizeof(uint32_t)));
    h->charbox   = static_cast<vocr_charbox*>(a16(VOCR_CHARBOX_MAX * sizeof(vocr_charbox)));
    h->align_map = static_cast<int16_t*>(a16(kTextCap * sizeof(int16_t)));
    if (!h->topv || !h->topc || !h->blank || !h->best || !h->prof || !h->charbox || !h->align_map) {
        release(h); return 0;
    }

    LOGI("VOCR ready: blob=%zu arena=%zu lm=%s", h->blob_bytes, h->arena_bytes, h->has_lm ? "on" : "off");
    return reinterpret_cast<jlong>(h);
}

// Computes per-character boxes in crop-pixel space for the text we will return.
// Strategy:
//   - greedy text (LM unused or unchanged): 1:1 charbox[i], exact
//   - LM text differs: try vocr_charbox_align to map LM chars onto greedy charboxes
//     (only if the exact signature exists in vocr_charbox.h; see the CALL ALIGN block)
//   - any insertion, or align failed: return zero exact boxes → Kotlin falls back to uniform
struct CharOut { float x0, x1; };   // x0 == -1.0f means "no exact box for this char"

static std::vector<CharOut> build_charboxes(VocrHandle* h, const vocr_image& img,
                                            unsigned frames, const char* final_text) {
    std::vector<CharOut> out;
    if (frames == 0) return out;

    unsigned ngreedy = 0;
    if (vocr_charbox_profile(&img, h->prof) != 0) return out;
    static const vocr_charbox_cal kCal = { 0, { 0, -45, -32, 48, -23, -4, 0, -6 } };
    if (vocr_charbox_find(&h->model, h->best, 1, frames,
                          img.width, img.height, 0, /* plain cells (VOCR_CHARBOX_CELL is not a real flag) */
                          &kCal, nullptr, h->prof,
                          h->charbox, VOCR_CHARBOX_MAX, &ngreedy) != 0) {
        return out;
    }
    if (ngreedy == 0) return out;

    const bool same_as_greedy = (std::strcmp(final_text, h->greedy) == 0);

    if (same_as_greedy) {
        out.reserve(ngreedy);
        for (unsigned i = 0; i < ngreedy; i++)
            out.push_back({ h->charbox[i].x0 / 16.0f, h->charbox[i].x1 / 16.0f });
        return out;
    }

    // ─── CALL ALIGN ────────────────────────────────────────────────────────────
    // The LM changed the text. Ask vocr_charbox_align to map LM chars onto greedy
    // charboxes. Insertions (map[j] == -1) force a uniform fallback for the whole
    // line, which is safe (uniform = coarse but never highlights the wrong glyph).
    //
    // VERIFY the signature against vocr_charbox.h before building. If the header
    // exposes it, keep the call; if not, delete it and the caller falls back to
    // uniform (same as the current behaviour).
    //
    //   int vocr_charbox_align(const char* src, unsigned n,
    //                          const char* dst, unsigned m, int16_t* map);
    //
    // Per the README, "map[j] is a character of src or -1".
    //
    // Uncomment once the signature is confirmed:

    // unsigned m = static_cast<unsigned>(std::strlen(final_text));
    // if (vocr_charbox_align(h->greedy, ngreedy, final_text, m, h->align_map) == 0) {
    //     out.reserve(m);
    //     for (unsigned j = 0; j < m; j++) {
    //         int16_t mi = h->align_map[j];
    //         if (mi < 0 || static_cast<unsigned>(mi) >= ngreedy) return {};   // insertion → uniform
    //         out.push_back({ h->charbox[mi].x0 / 16.0f, h->charbox[mi].x1 / 16.0f });
    //     }
    // }
    // ──────────────────────────────────────────────────────────────────────────

    return out;   // empty if align is unavailable → Kotlin falls back to uniform
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_chimahon_local_ocr_VocrInterpreter_nativeRecognizeLine(
        JNIEnv* env, jclass, jlong handle,
        jbyteArray jGrey, jint w, jint h, jboolean invert, jboolean useLm) {
    auto* vh = reinterpret_cast<VocrHandle*>(handle);
    if (!vh) return nullptr;
    if (env->GetArrayLength(jGrey) != static_cast<jsize>(w) * h) return nullptr;

    std::lock_guard<std::mutex> g(vh->lock);
    jbyte* px = env->GetByteArrayElements(jGrey, nullptr);

    vocr_image img = {};
    img.pixels = reinterpret_cast<const unsigned char*>(px);
    img.width  = static_cast<unsigned>(w);
    img.height = static_cast<unsigned>(h);
    img.stride = static_cast<size_t>(w);
    img.format = static_cast<int>(VOCR_GREY8) | (invert ? VOCR_INVERT : 0);

    vocr_extra ex = {};
    ex.cap_frames  = kMaxFrames;
    ex.best        = vh->best;
    ex.topk        = kTopK;
    ex.topk_logit  = vh->topv;
    ex.topk_class  = vh->topc;
    ex.blank_logit = vh->blank;

    int rc = vocr_run(&vh->ctx, &img, vh->greedy, sizeof(vh->greedy), &ex);

    bool lm_ok = false;
    int  lm_rc = 0;
    if (rc == 0 && useLm && vh->has_lm) {
        unsigned chars = 0;
        lm_rc = vocr_lm_decode(&vh->lm_ctx, &ex, vh->lmtext, sizeof(vh->lmtext), &chars);
        lm_ok = (lm_rc == 0);
    }

    const char* final_text = (rc == 0) ? (lm_ok ? vh->lmtext : vh->greedy) : "";
    std::vector<CharOut> boxes;
    if (rc == 0) boxes = build_charboxes(vh, img, ex.frames, final_text);

    env->ReleaseByteArrayElements(jGrey, px, JNI_ABORT);

    jclass strCls = env->FindClass("java/lang/String");
    jobjectArray out = env->NewObjectArray(7 + static_cast<jsize>(boxes.size()) * 2, strCls, nullptr);
    auto set = [&](jsize i, const char* s) { env->SetObjectArrayElement(out, i, env->NewStringUTF(s)); };
    char buf[64];

    if (rc != 0) {
        std::snprintf(buf, sizeof buf, "vocr_run: %d", rc);
        set(0, ""); set(1, ""); set(2, "0"); set(3, "0"); set(4, "0"); set(5, ""); set(6, buf);
        return out;
    }

    set(0, vh->greedy);
    set(1, lm_ok ? vh->lmtext : vh->greedy);
    std::snprintf(buf, sizeof buf, "%.6f", ex.conf_min);  set(2, buf);
    std::snprintf(buf, sizeof buf, "%.6f", ex.conf_mean); set(3, buf);
    std::snprintf(buf, sizeof buf, "%u", ex.frames);      set(4, buf);
    set(5, (lm_ok && std::strcmp(vh->greedy, vh->lmtext) == 0) ? "1" : "");
    if (lm_rc != 0) { std::snprintf(buf, sizeof buf, "vocr_lm_decode: %d", lm_rc); set(6, buf); }
    else { set(6, ""); }

    for (size_t i = 0; i < boxes.size(); i++) {
        std::snprintf(buf, sizeof buf, "%.4f", boxes[i].x0); set(7 + static_cast<jsize>(i) * 2 + 0, buf);
        std::snprintf(buf, sizeof buf, "%.4f", boxes[i].x1); set(7 + static_cast<jsize>(i) * 2 + 1, buf);
    }
    return out;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_chimahon_local_ocr_VocrInterpreter_nativeFindTextBlocks(
        JNIEnv* env, jclass, jlong handle, jbyteArray jGrey, jint w, jint h) {
    auto* vh = reinterpret_cast<VocrHandle*>(handle);
    if (!vh || !vh->spotter_blob || env->GetArrayLength(jGrey) != w * h) return nullptr;

    jbyte* px = env->GetByteArrayElements(jGrey, nullptr);
    VocrLinesImage img = {};
    img.pixels = reinterpret_cast<const unsigned char*>(px);
    img.width = w; img.height = h; img.stride = w; img.bpp = VOCR_LINES_GREY;

    size_t need = vocr_textspot6_arena_bytes(&vh->spotter, w, h);
    void* arena = a16(need);
    if (!arena) { env->ReleaseByteArrayElements(jGrey, px, JNI_ABORT); return nullptr; }
    VocrTextspot6Set set = {};
    
    int rc = vocr_textspot6_find(&vh->spotter, &img, 0, arena, need, &set);
    std::free(arena);
    env->ReleaseByteArrayElements(jGrey, px, JNI_ABORT);

    if (rc || set.count == 0) return nullptr;

    // Separate horizontal and vertical boxes
    VocrT7Box h_boxes[VOCR_T7_MAX_BOXES];
    unsigned n_h = 0;
    VocrTextspot6Box v_boxes[VOCR_T7_MAX_BOXES];
    unsigned n_v = 0;

    for (unsigned i = 0; i < set.count; i++) {
        if (set.boxes[i].vertical) {
            if (n_v < VOCR_T7_MAX_BOXES) v_boxes[n_v++] = set.boxes[i];
        } else {
            if (n_h < VOCR_T7_MAX_BOXES) {
                h_boxes[n_h].x = set.boxes[i].x;
                h_boxes[n_h].y = set.boxes[i].y;
                h_boxes[n_h].w = set.boxes[i].w;
                h_boxes[n_h].h = set.boxes[i].h;
                h_boxes[n_h].conf = set.boxes[i].conf;
                h_boxes[n_h].dark = set.boxes[i].dark;
                n_h++;
            }
        }
    }

    // Process horizontal boxes with the author's exact pick logic: JOIN10 | EXTEND
    VocrT7Box out_h[VOCR_T7_MAX_BOXES];
    unsigned out_nh = 0;
    if (n_h > 0) {
        unsigned steps = VOCR_T7_JOIN10 | VOCR_T7_EXTEND;
        size_t t7_need = vocr_textspot7_arena_bytes_steps(w, h, steps);
        void* t7_arena = a16(t7_need);
        if (t7_arena) {
            vocr_textspot7_post(h_boxes, n_h, 0, 0, w, h, &img, steps, t7_arena, t7_need, out_h, &out_nh);
            std::free(t7_arena);
        } else {
            for (unsigned i = 0; i < n_h; i++) out_h[i] = h_boxes[i];
            out_nh = n_h;
        }
    }

    unsigned total = out_nh + n_v;
    if (total == 0) return nullptr;

    jclass intArrCls = env->FindClass("[I");
    jobjectArray out = env->NewObjectArray(total, intArrCls, nullptr);
    
    int idx = 0;
    for (unsigned i = 0; i < out_nh; i++) {
        jintArray jbox = env->NewIntArray(6);
        jint tmp[6] = { out_h[i].x, out_h[i].y, out_h[i].w, out_h[i].h, static_cast<jint>(out_h[i].dark), 0 };
        env->SetIntArrayRegion(jbox, 0, 6, tmp);
        env->SetObjectArrayElement(out, idx++, jbox);
        env->DeleteLocalRef(jbox);
    }
    for (unsigned i = 0; i < n_v; i++) {
        jintArray jbox = env->NewIntArray(6);
        jint tmp[6] = { v_boxes[i].x, v_boxes[i].y, v_boxes[i].w, v_boxes[i].h, v_boxes[i].dark, 1 };
        env->SetIntArrayRegion(jbox, 0, 6, tmp);
        env->SetObjectArrayElement(out, idx++, jbox);
        env->DeleteLocalRef(jbox);
    }
    return out;
}

extern "C" JNIEXPORT jintArray JNICALL
Java_chimahon_local_ocr_VocrInterpreter_nativeFindLines(
        JNIEnv* env, jclass, jbyteArray jGrey, jint w, jint h) {
    if (env->GetArrayLength(jGrey) != static_cast<jsize>(w) * h) return nullptr;
    jbyte* px = env->GetByteArrayElements(jGrey, nullptr);
    VocrLinesImage img = {};
    img.pixels = reinterpret_cast<const unsigned char*>(px); img.width = w; img.height = h;
    img.stride = static_cast<size_t>(w); img.bpp = VOCR_LINES_GREY; /* 1 = grey, 4 = RGBA */
    size_t need = vocr_lines_arena_bytes(w, h);
    void* arena = a16(need);
    if (!arena) { env->ReleaseByteArrayElements(jGrey, px, JNI_ABORT); return nullptr; }
    VocrLineSet set = {};
    int rc = vocr_find_lines(&img, VOCR_LINES_GRAD, arena, need, &set);
    std::free(arena);
    env->ReleaseByteArrayElements(jGrey, px, JNI_ABORT);
    if (rc) { LOGE("vocr_find_lines: %d", rc); return nullptr; }
    jintArray out = env->NewIntArray(static_cast<jsize>(set.count) * 5);
    jint tmp[VOCR_LINES_MAX * 5];
    for (unsigned i = 0; i < set.count; i++) {
        tmp[i*5+0] = set.lines[i].x; tmp[i*5+1] = set.lines[i].y;
        tmp[i*5+2] = set.lines[i].w; tmp[i*5+3] = set.lines[i].h;
        tmp[i*5+4] = set.lines[i].dark ? 1 : 0;
    }
    env->SetIntArrayRegion(out, 0, static_cast<jsize>(set.count) * 5, tmp);
    return out;
}

extern "C" JNIEXPORT void JNICALL
Java_chimahon_local_ocr_VocrInterpreter_nativeDestroy(JNIEnv*, jclass, jlong handle) {
    release(reinterpret_cast<VocrHandle*>(handle));
}

extern "C" JNIEXPORT void JNICALL
Java_chimahon_local_ocr_VocrInterpreter_nativeRotateColumn(
        JNIEnv* env, jclass, jbyteArray jSrc, jint w, jint h, jbyteArray jDst) {
    jbyte* src = env->GetByteArrayElements(jSrc, nullptr);
    jbyte* dst = env->GetByteArrayElements(jDst, nullptr);
    vocr_column_rotate(reinterpret_cast<const unsigned char*>(src), w, VOCR_LINES_GREY, w, h, 
                       reinterpret_cast<unsigned char*>(dst), h);
    env->ReleaseByteArrayElements(jDst, dst, 0);
    env->ReleaseByteArrayElements(jSrc, src, JNI_ABORT);
}
