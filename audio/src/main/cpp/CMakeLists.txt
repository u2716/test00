cmake_minimum_required(VERSION 3.22.1)

project(word_audio_jni LANGUAGES C CXX)

# Force optimization even for debug builds, because hoshidicts and audio JNI
# are computationally heavy and run 5x slower without it.
if (CMAKE_BUILD_TYPE STREQUAL "Debug")
    set(CMAKE_CXX_FLAGS_DEBUG "-O3 -DNDEBUG")
endif()

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ─── SQLite amalgamation as a private static lib ────────────────────────
add_library(sqlite_amalgamation STATIC
    sqlite/sqlite3.c
)
target_include_directories(sqlite_amalgamation PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}/sqlite
)
target_compile_definitions(sqlite_amalgamation PUBLIC
    SQLITE_THREADSAFE=1
    SQLITE_ENABLE_DESERIALIZE=1
    SQLITE_OMIT_LOAD_EXTENSION=1
    SQLITE_OMIT_AUTHORIZATION=1
    SQLITE_OMIT_EXPLAIN=1
    SQLITE_OMIT_DEPRECATED=1
    SQLITE_OMIT_FOREIGN_KEY=1
    SQLITE_OMIT_BUILTIN_TEST=1
    SQLITE_OMIT_INCRBLOB=1
    SQLITE_OMIT_UPSERT=1
    SQLITE_OMIT_JSON=1
    SQLITE_OMIT_DATETIME_FUNCS=1
    SQLITE_OMIT_PROGRESS_CALLBACK=1
    SQLITE_TEMP_STORE=2
    SQLITE_DEFAULT_MEMSTATUS=0
)
target_compile_options(sqlite_amalgamation PRIVATE
    -Os
    -ffunction-sections
    -fdata-sections
)

# ─── Word Audio JNI shim ────────────────────────────────────────────────
add_library(word_audio_jni SHARED
    word_audio_jni.cpp
)
find_library(log-lib log)
target_link_libraries(word_audio_jni PRIVATE
    sqlite_amalgamation
    ${log-lib}
)
target_compile_options(word_audio_jni PRIVATE
    -Os
    -fvisibility=hidden
    -ffunction-sections
    -fdata-sections
)
target_link_options(word_audio_jni PRIVATE
    -Wl,--gc-sections
    -Wl,-z,noexecstack
    -Wl,-z,relro
    -Wl,-z,now
)
