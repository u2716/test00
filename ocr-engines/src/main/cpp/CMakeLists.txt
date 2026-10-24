cmake_minimum_required(VERSION 3.22.1)
project("chimahon_ocr_local")

# Force optimization even for debug builds, because hoshidicts and audio JNI
# are computationally heavy and run 5x slower without it.
if (CMAKE_BUILD_TYPE STREQUAL "Debug")
    set(CMAKE_CXX_FLAGS_DEBUG "-O3 -DNDEBUG")
endif()

# --------------------------------------------------------------------------
# MNN (built from the third-party/MNN git submodule)
# --------------------------------------------------------------------------
# OCR-only build — no LLM / vision / audio / diffusion backends.
# Each flag below is read from cache by MNN's own CMakeLists.
set(MNN_REDUCE_SIZE             ON  CACHE BOOL "")
set(MNN_BUILD_EXPRESS           OFF CACHE BOOL "")
set(MNN_BUILD_LLM               OFF CACHE BOOL "")
set(MNN_BUILD_LLM_OMNI          OFF CACHE BOOL "")
set(MNN_BUILD_OPENCV            OFF CACHE BOOL "")
set(MNN_BUILD_AUDIO             OFF CACHE BOOL "")
set(MNN_IMGCODECS               OFF CACHE BOOL "")
set(MNN_BUILD_DIFFUSION         OFF CACHE BOOL "")
set(MNN_LLM_BUILD_DEMO          OFF CACHE BOOL "")
set(LLM_SUPPORT_HTTP_RESOURCE   OFF CACHE BOOL "")
set(MNN_KLEIDIAI                OFF CACHE BOOL "")
set(MNN_BUILD_SHARED_LIBS       ON  CACHE BOOL "")
set(MNN_SEP_BUILD               OFF CACHE BOOL "")
set(MNN_USE_LOGCAT              ON  CACHE BOOL "")
set(MNN_ARM82                   ON  CACHE BOOL "")
set(MNN_OPENCL                  OFF CACHE BOOL "")

add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/third-party/MNN build-mnn)

# --------------------------------------------------------------------------
# MNN OCR JNI shim (Meiki OCR via MNN)
# --------------------------------------------------------------------------
add_library(mnn_ocr_jni SHARED mnn_ocr_jni.cpp)
target_include_directories(mnn_ocr_jni PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/third-party/MNN/include
)
target_link_libraries(mnn_ocr_jni PRIVATE MNN android log)
target_link_options(mnn_ocr_jni PRIVATE
    -Wl,--gc-sections
    -Wl,-z,noexecstack
    -Wl,-z,relro
    -Wl,-z,now
    -s
)

# lens_asset_init — seeds the MediaPipe Singleton<AssetManager> before
# NativeCreateEngineManager is invoked by liblens_ondevice_engine_play_ml.so.
# Uses dlopen/dlsym to call InitializeFromActivity on the singleton, or falls
# back to direct GOT-patching of the in-memory singleton offset.
add_library(lens_asset_init SHARED lens_asset_init.cpp)

find_library(log-lib  log)
find_library(android-lib android)

target_link_libraries(lens_asset_init
    ${log-lib}
    ${android-lib}
    dl
)

target_link_options(lens_asset_init PRIVATE -s)
