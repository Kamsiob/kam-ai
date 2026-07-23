cmake_minimum_required(VERSION 3.22.1)
project("kamai")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# llama.cpp is fetched, not committed. fetch_llama.sh pins the tag; see
# tools/fetch_llama.sh and DECISIONS.md.
set(LLAMA_DIR "${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp")

if(NOT EXISTS "${LLAMA_DIR}/CMakeLists.txt")
    message(FATAL_ERROR
        "llama.cpp is missing from ${LLAMA_DIR}.\n"
        "Run tools/fetch_llama.sh from the repository root first.")
endif()

# Only the library. No examples, no tools, no tests, no server.
set(LLAMA_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL           OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP          OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE       OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS    OFF CACHE BOOL "" FORCE)

# arm64 dot-product and matrix-multiply extensions. Every phone this app will
# run on has them, and they are worth a large fraction of the token rate.
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
add_compile_options(-march=armv8.2-a+dotprod+i8mm+fp16)

add_subdirectory("${LLAMA_DIR}" llama_build)

add_library(kamai SHARED kamai_llama.cpp)

target_link_libraries(kamai
    PRIVATE
        llama
        ggml
        android
        log)

target_compile_options(kamai PRIVATE -O3 -fvisibility=hidden)

# Keep ggml and llama symbols internal to this library. Without this the static
# archives export thousands of ggml symbols, which would collide at runtime with
# the separate, different-versioned ggml inside libkamwhisper.so. Only the JNI
# entry points (marked JNIEXPORT, default visibility) stay exported.
target_link_options(kamai PRIVATE -Wl,--exclude-libs,ALL)

# ---------------------------------------------------------------------------
# whisper.cpp, built completely separately from llama.cpp.
#
# Both vendor ggml, and the two copies are different versions with dozens of
# public API differences, so they cannot share one ggml target. They also cannot
# both be add_subdirectory'd into this project: the target name `ggml` would
# collide. So whisper is built in its own isolated CMake invocation with
# ExternalProject, producing static libraries that are linked into their own
# shared object, libkamwhisper.so. With -fvisibility=hidden on both libraries and
# Android's default per-library symbol namespace, the two ggml copies never see
# each other at runtime.
# ---------------------------------------------------------------------------
set(WHISPER_DIR "${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp")

if(NOT EXISTS "${WHISPER_DIR}/CMakeLists.txt")
    message(FATAL_ERROR
        "whisper.cpp is missing from ${WHISPER_DIR}.\n"
        "Run tools/fetch_whisper.sh from the repository root first.")
endif()

include(ExternalProject)

set(WHISPER_BUILD "${CMAKE_CURRENT_BINARY_DIR}/whisper_build")
set(WHISPER_LIBS
    "${WHISPER_BUILD}/src/libwhisper.a"
    "${WHISPER_BUILD}/ggml/src/libggml.a"
    "${WHISPER_BUILD}/ggml/src/libggml-base.a"
    "${WHISPER_BUILD}/ggml/src/libggml-cpu.a")

ExternalProject_Add(whisper_ext
    SOURCE_DIR "${WHISPER_DIR}"
    BINARY_DIR "${WHISPER_BUILD}"
    CMAKE_GENERATOR "${CMAKE_GENERATOR}"
    CMAKE_ARGS
        -DCMAKE_MAKE_PROGRAM=${CMAKE_MAKE_PROGRAM}
        -DCMAKE_TOOLCHAIN_FILE=${CMAKE_TOOLCHAIN_FILE}
        -DANDROID_ABI=${ANDROID_ABI}
        -DANDROID_PLATFORM=${ANDROID_PLATFORM}
        -DANDROID_NDK=${ANDROID_NDK}
        -DCMAKE_BUILD_TYPE=Release
        -DBUILD_SHARED_LIBS=OFF
        -DWHISPER_BUILD_TESTS=OFF
        -DWHISPER_BUILD_EXAMPLES=OFF
        -DWHISPER_BUILD_SERVER=OFF
        -DGGML_OPENMP=OFF
        -DGGML_LLAMAFILE=OFF
        -DGGML_NATIVE=OFF
        "-DCMAKE_C_FLAGS=-march=armv8.2-a+dotprod+i8mm+fp16 -O3"
        "-DCMAKE_CXX_FLAGS=-march=armv8.2-a+dotprod+i8mm+fp16 -O3"
    BUILD_BYPRODUCTS ${WHISPER_LIBS}
    INSTALL_COMMAND "")

add_library(kamwhisper SHARED kamai_whisper.cpp)
add_dependencies(kamwhisper whisper_ext)

target_include_directories(kamwhisper PRIVATE
    "${WHISPER_DIR}/include"
    "${WHISPER_DIR}/ggml/include")

# Whole-archive so ggml's CPU backend registration is not stripped from the
# static libraries. The order matters: whisper depends on ggml, ggml on
# ggml-base.
target_link_libraries(kamwhisper
    PRIVATE
        -Wl,--whole-archive
        ${WHISPER_LIBS}
        -Wl,--no-whole-archive
        android
        log)

target_compile_options(kamwhisper PRIVATE -O3 -fvisibility=hidden)

# Same reasoning as libkamai above: keep this library's ggml copy fully internal
# so it cannot collide with the other one. Only the WhisperBridge JNI entry
# points stay exported.
target_link_options(kamwhisper PRIVATE -Wl,--exclude-libs,ALL)
