cmake_minimum_required(VERSION 3.22.1)
project("chimahon_ocr_local")

# Force optimization even for debug builds, because hoshidicts and audio JNI
# are computationally heavy and run 5x slower without it.
if (CMAKE_BUILD_TYPE STREQUAL "Debug")
    set(CMAKE_CXX_FLAGS_DEBUG "-O3 -DNDEBUG")
endif()

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
