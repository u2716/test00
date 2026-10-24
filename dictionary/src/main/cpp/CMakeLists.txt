cmake_minimum_required(VERSION 3.22.1)

project(hoshidicts_jni LANGUAGES C CXX)

# Force optimization even for debug builds, because hoshidicts and audio JNI
# are computationally heavy and run 5x slower without it.
if (CMAKE_BUILD_TYPE STREQUAL "Debug")
    set(CMAKE_CXX_FLAGS_DEBUG "-O3 -DNDEBUG")
endif()

set(CMAKE_CXX_STANDARD 23)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_subdirectory(hoshidicts)

add_library(hoshidicts_jni SHARED
    hoshidicts_jni.cpp
)

target_link_libraries(hoshidicts_jni PRIVATE
    hoshidicts
)

target_link_options(hoshidicts_jni PRIVATE -s)
