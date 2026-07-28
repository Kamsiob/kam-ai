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

# Set through CMAKE_*_FLAGS rather than add_compile_options, which is not a
# style preference. ggml decides which KleidiAI microkernels to compile by
# regexing CMAKE_C_FLAGS for "+dotprod" and "+i8mm". Flags added through
# add_compile_options never appear there, so ggml concluded this device had
# neither, skipped exactly the kernels its own wrappers call, and the build
# failed at link with undefined kai_* symbols. The flags were reaching the
# compiler the whole time; they were not reaching ggml's decision.
set(KAMAI_ARCH_FLAGS "-march=armv8.2-a+dotprod+i8mm+fp16")
set(CMAKE_C_FLAGS   "${CMAKE_C_FLAGS} ${KAMAI_ARCH_FLAGS}")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} ${KAMAI_ARCH_FLAGS}")

# KleidiAI: ARM's int8 matmul microkernels, issue #51.
#
# ggml can fetch these itself, but only at CMake configure time from inside the
# Gradle build, and when that download does not happen the wrappers still
# compile, the kai_* symbols are never defined, and it surfaces as a link error
# a long way from its cause. That is why this was off.
#
# So KleidiAI is vendored the same way llama.cpp is: tools/fetch_kleidiai.sh
# pulls the tag ggml pins, checks the same MD5 ggml checks, and puts it beside
# llama.cpp. Pointing FetchContent at that directory means ggml finds it already
# present and never reaches the network, so the build is reproducible offline and
# the dependency is explicit rather than implied.
if(EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/kleidiai/CMakeLists.txt")
    set(GGML_CPU_KLEIDIAI ON CACHE BOOL "" FORCE)
    # FetchContent honours FETCHCONTENT_SOURCE_DIR_<NAME> with the name
    # uppercased, and ggml declares this one as KleidiAI_Download.
    set(FETCHCONTENT_SOURCE_DIR_KLEIDIAI_DOWNLOAD
        "${CMAKE_CURRENT_SOURCE_DIR}/kleidiai" CACHE PATH "" FORCE)
    message(STATUS "KleidiAI: using vendored sources")
else()
    # Not fatal. The app builds and runs without it, just without the ARM
    # microkernels, and a missing optional optimisation should not stop somebody
    # building the project.
    set(GGML_CPU_KLEIDIAI OFF CACHE BOOL "" FORCE)
    message(STATUS "KleidiAI: not present, run tools/fetch_kleidiai.sh to enable")
endif()


add_subdirectory("${LLAMA_DIR}" llama_build)

add_library(kamai SHARED kamai_llama.cpp)

target_link_libraries(kamai
    PRIVATE
        llama
        ggml
        android
        log)

# Codegen and link flags, issue #51.
#
# Every one of these is about what ends up in the .so rather than how fast the
# arithmetic is: the maths is already handled by -O3 and the arm64 extensions
# above. A 45 MB native library is most of the download, and dead code in it
# costs the user data and disk for nothing.
#
#   -ffunction-sections / -fdata-sections with --gc-sections lets the linker drop
#   anything unreachable, which in a vendored llama.cpp is a great deal: the app
#   uses a fraction of what the library can do.
#
#   --icf=all folds functions with identical bodies into one. Templated code
#   generates a lot of these.
#
#   -fvisibility=hidden was already here and is doing more work now: with
#   sections and gc, hidden symbols are genuinely removable rather than merely
#   unexported.
#
#   max-page-size=16384 is not an optimisation. Android 15 devices can boot with
#   16 KB pages, and a library linked for 4 KB will not load on them at all. It
#   costs a little padding and buys the app still working on hardware that is
#   already shipping.
target_compile_options(kamai PRIVATE
    -O3
    -fvisibility=hidden
    -fvisibility-inlines-hidden
    -ffunction-sections
    -fdata-sections)

target_link_options(kamai PRIVATE
    -Wl,--gc-sections
    -Wl,--icf=all
    -Wl,-z,max-page-size=16384)

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

target_compile_options(kamwhisper PRIVATE
    -O3
    -fvisibility=hidden
    -fvisibility-inlines-hidden
    -ffunction-sections
    -fdata-sections)

target_link_options(kamwhisper PRIVATE
    -Wl,--gc-sections
    -Wl,--icf=all
    -Wl,-z,max-page-size=16384)

# Same reasoning as libkamai above: keep this library's ggml copy fully internal
# so it cannot collide with the other one. Only the WhisperBridge JNI entry
# points stay exported.
target_link_options(kamwhisper PRIVATE -Wl,--exclude-libs,ALL)
