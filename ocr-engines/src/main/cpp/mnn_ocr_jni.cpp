// mnn_ocr_jni.cpp — JNI shim over MNN's Session API for Meiki OCR.
//
// Adapted from playtranslate's mnn_infer.cpp (Session portion only).
// Deliberately dumb: load a .mnn model, run typed named tensors through it,
// return typed named tensors back. All OCR logic lives in Kotlin.
//
// Links against libMNN.so (built from the third-party/MNN submodule via
// CMake's add_subdirectory).

#include <jni.h>
#include <cstring>
#include <map>
#include <string>
#include <vector>

#include <android/log.h>
#include <MNN/Interpreter.hpp>
#include <MNN/Tensor.hpp>
#include <MNN/MNNForwardType.h>

#define TAG "mnn-ocr"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace {

struct OcrModel {
    MNN::Interpreter *net = nullptr;
    MNN::Session *session = nullptr;
};

MNN::BackendConfig::PrecisionMode precisionFromFlag(jint flag) {
    switch (flag) {
        case 1:  return MNN::BackendConfig::Precision_High;
        case 2:  return MNN::BackendConfig::Precision_Low;
        default: return MNN::BackendConfig::Precision_Normal;
    }
}

} // namespace

extern "C"
JNIEXPORT jlong JNICALL
Java_chimahon_local_ocr_mnn_MnnInterpreter_nativeCreate(
        JNIEnv *env, jobject /*unused*/, jstring jpath, jint numThread, jint precision) {
    const char *path = env->GetStringUTFChars(jpath, nullptr);
    MNN::Interpreter *net = MNN::Interpreter::createFromFile(path);
    LOGI("MnnInterpreter: createFromFile %s -> %p", path, (void *) net);
    env->ReleaseStringUTFChars(jpath, path);
    if (net == nullptr) {
        LOGE("MnnInterpreter: createFromFile returned null");
        return 0;
    }

    MNN::ScheduleConfig config;
    config.type = MNN_FORWARD_CPU;
    config.numThread = numThread > 0 ? numThread : 4;
    MNN::BackendConfig backendConfig;
    backendConfig.precision = precisionFromFlag(precision);
    backendConfig.power = MNN::BackendConfig::Power_Normal;
    config.backendConfig = &backendConfig;

    MNN::Session *session = net->createSession(config);
    if (session == nullptr) {
        LOGE("MnnInterpreter: createSession returned null");
        MNN::Interpreter::destroy(net);
        return 0;
    }

    auto *model = new OcrModel{net, session};
    return reinterpret_cast<jlong>(model);
}

extern "C"
JNIEXPORT jobjectArray JNICALL
Java_chimahon_local_ocr_mnn_MnnInterpreter_nativeRunMulti(
        JNIEnv *env, jobject /*unused*/, jlong handle,
        jobjectArray jNames, jobjectArray jShapes, jintArray jDtypes, jobjectArray jData) {
    auto *model = reinterpret_cast<OcrModel *>(handle);
    if (model == nullptr || model->net == nullptr || model->session == nullptr) {
        LOGE("nativeRunMulti: invalid handle");
        return nullptr;
    }
    MNN::Interpreter *net = model->net;
    MNN::Session *session = model->session;

    const jsize nIn = env->GetArrayLength(jNames);
    jint *dtypes = env->GetIntArrayElements(jDtypes, nullptr);
    const std::map<std::string, MNN::Tensor *> inputs = net->getSessionInputAll(session);

    std::vector<std::string> names((size_t) nIn);
    for (jsize i = 0; i < nIn; ++i) {
        auto jn = (jstring) env->GetObjectArrayElement(jNames, i);
        const char *c = env->GetStringUTFChars(jn, nullptr);
        names[i] = c;
        env->ReleaseStringUTFChars(jn, c);
        env->DeleteLocalRef(jn);

        auto js = (jintArray) env->GetObjectArrayElement(jShapes, i);
        const jsize sl = env->GetArrayLength(js);
        jint *se = env->GetIntArrayElements(js, nullptr);
        std::vector<int> dims(se, se + sl);
        env->ReleaseIntArrayElements(js, se, JNI_ABORT);
        env->DeleteLocalRef(js);

        auto it = inputs.find(names[i]);
        if (it == inputs.end()) {
            LOGE("nativeRunMulti: no session input named '%s'", names[i].c_str());
            env->ReleaseIntArrayElements(jDtypes, dtypes, JNI_ABORT);
            return nullptr;
        }
        net->resizeTensor(it->second, dims);
    }
    net->resizeSession(session);

    for (jsize i = 0; i < nIn; ++i) {
        MNN::Tensor *t = inputs.at(names[i]);
        auto *host = new MNN::Tensor(t, t->getDimensionType());
        const int count = host->elementSize();
        auto jd = env->GetObjectArrayElement(jData, i);
        if (dtypes[i] == 1) {
            auto ja = (jintArray) jd;
            if (env->GetArrayLength(ja) != count) {
                LOGE("nativeRunMulti: int input '%s' len mismatch", names[i].c_str());
                delete host; env->DeleteLocalRef(jd);
                env->ReleaseIntArrayElements(jDtypes, dtypes, JNI_ABORT);
                return nullptr;
            }
            jint *p = env->GetIntArrayElements(ja, nullptr);
            memcpy(host->host<int32_t>(), p, (size_t) count * sizeof(int32_t));
            env->ReleaseIntArrayElements(ja, p, JNI_ABORT);
        } else {
            auto ja = (jfloatArray) jd;
            if (env->GetArrayLength(ja) != count) {
                LOGE("nativeRunMulti: float input '%s' len mismatch", names[i].c_str());
                delete host; env->DeleteLocalRef(jd);
                env->ReleaseIntArrayElements(jDtypes, dtypes, JNI_ABORT);
                return nullptr;
            }
            jfloat *p = env->GetFloatArrayElements(ja, nullptr);
            memcpy(host->host<float>(), p, (size_t) count * sizeof(float));
            env->ReleaseFloatArrayElements(ja, p, JNI_ABORT);
        }
        t->copyFromHostTensor(host);
        delete host;
        env->DeleteLocalRef(jd);
    }
    env->ReleaseIntArrayElements(jDtypes, dtypes, JNI_ABORT);

    if (net->runSession(session) != MNN::NO_ERROR) {
        LOGE("nativeRunMulti: runSession failed");
        return nullptr;
    }

    const std::map<std::string, MNN::Tensor *> outputs = net->getSessionOutputAll(session);
    const jsize nOut = (jsize) outputs.size();
    jclass strCls = env->FindClass("java/lang/String");
    jclass objCls = env->FindClass("java/lang/Object");
    jobjectArray outNames = env->NewObjectArray(nOut, strCls, nullptr);
    jintArray outDtypes = env->NewIntArray(nOut);
    jobjectArray outShapes = env->NewObjectArray(nOut, objCls, nullptr);
    jobjectArray outData = env->NewObjectArray(nOut, objCls, nullptr);
    std::vector<jint> dtypeBuf((size_t) nOut);

    jsize oi = 0;
    for (const auto &kv : outputs) {
        MNN::Tensor *t = kv.second;
        auto *host = new MNN::Tensor(t, t->getDimensionType());
        t->copyToHostTensor(host);
        const int count = host->elementSize();
        const std::vector<int> shp = host->shape();

        jstring jn = env->NewStringUTF(kv.first.c_str());
        env->SetObjectArrayElement(outNames, oi, jn);
        env->DeleteLocalRef(jn);

        jintArray jshp = env->NewIntArray((jsize) shp.size());
        { std::vector<jint> tmp(shp.begin(), shp.end());
          env->SetIntArrayRegion(jshp, 0, (jsize) tmp.size(), tmp.data()); }
        env->SetObjectArrayElement(outShapes, oi, jshp);
        env->DeleteLocalRef(jshp);

        const bool isFloat = (host->getType().code == halide_type_float);
        dtypeBuf[(size_t) oi] = isFloat ? 0 : 1;
        if (isFloat) {
            jfloatArray jd = env->NewFloatArray(count);
            env->SetFloatArrayRegion(jd, 0, count, host->host<float>());
            env->SetObjectArrayElement(outData, oi, jd);
            env->DeleteLocalRef(jd);
        } else {
            jintArray jd = env->NewIntArray(count);
            env->SetIntArrayRegion(jd, 0, count, (const jint *) host->host<int32_t>());
            env->SetObjectArrayElement(outData, oi, jd);
            env->DeleteLocalRef(jd);
        }
        delete host;
        ++oi;
    }
    env->SetIntArrayRegion(outDtypes, 0, nOut, dtypeBuf.data());

    jobjectArray result = env->NewObjectArray(4, objCls, nullptr);
    env->SetObjectArrayElement(result, 0, outNames);
    env->SetObjectArrayElement(result, 1, outDtypes);
    env->SetObjectArrayElement(result, 2, outShapes);
    env->SetObjectArrayElement(result, 3, outData);
    return result;
}

extern "C"
JNIEXPORT void JNICALL
Java_chimahon_local_ocr_mnn_MnnInterpreter_nativeDestroy(
        JNIEnv * /*env*/, jobject /*unused*/, jlong handle) {
    auto *model = reinterpret_cast<OcrModel *>(handle);
    if (model == nullptr) return;
    if (model->net != nullptr) {
        MNN::Interpreter::destroy(model->net);
    }
    delete model;
    LOGI("MnnInterpreter: destroyed handle");
}
