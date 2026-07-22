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
