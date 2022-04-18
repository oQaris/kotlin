/*
 * Copyright 2010-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#include "Alloc.h"

#include <cstdint>
#include <unistd.h>

#if KONAN_INTERNAL_DLMALLOC
extern "C" void* dlcalloc(size_t, size_t);
extern "C" void dlfree(void*);
#define calloc_impl dlcalloc
#define free_impl dlfree
#define calloc_aligned_impl(count, size, alignment) dlcalloc(count, size)

#else
extern "C" void* konan_calloc_impl(size_t, size_t);
extern "C" void konan_free_impl(void*);
extern "C" void* konan_calloc_aligned_impl(size_t count, size_t size, size_t alignment);
#define calloc_impl konan_calloc_impl
#define free_impl konan_free_impl
#define calloc_aligned_impl konan_calloc_aligned_impl
#endif

namespace konan {

void* calloc(size_t count, size_t size) {
    return calloc_impl(count, size);
}

void* calloc_aligned(size_t count, size_t size, size_t alignment) {
    return calloc_aligned_impl(count, size, alignment);
}

void free(void* pointer) {
    free_impl(pointer);
}

#if KONAN_INTERNAL_DLMALLOC
// This function is being called when memory allocator needs more RAM.

#if KONAN_WASM

namespace {

constexpr uint32_t MFAIL = ~(uint32_t)0;
constexpr uint32_t WASM_PAGESIZE_EXPONENT = 16;
constexpr uint32_t WASM_PAGESIZE = 1u << WASM_PAGESIZE_EXPONENT;
constexpr uint32_t WASM_PAGEMASK = WASM_PAGESIZE - 1;

uint32_t pageAlign(int32_t value) {
    return (value + WASM_PAGEMASK) & ~(WASM_PAGEMASK);
}

uint32_t inBytes(uint32_t pageCount) {
    return pageCount << WASM_PAGESIZE_EXPONENT;
}

uint32_t inPages(uint32_t value) {
    return value >> WASM_PAGESIZE_EXPONENT;
}

extern "C" void Konan_notify_memory_grow();

uint32_t memorySize() {
    return __builtin_wasm_memory_size(0);
}

int32_t growMemory(uint32_t delta) {
    int32_t oldLength = __builtin_wasm_memory_grow(0, delta);
    Konan_notify_memory_grow();
    return oldLength;
}

} // namespace

void* moreCore(int32_t delta) {
    uint32_t top = inBytes(memorySize());
    if (delta > 0) {
        if (growMemory(inPages(pageAlign(delta))) == 0) {
            return (void*)MFAIL;
        }
    } else if (delta < 0) {
        return (void*)MFAIL;
    }
    return (void*)top;
}

// dlmalloc() wants to know the page size.
long getpagesize() {
    return WASM_PAGESIZE;
}

#else
void* moreCore(int size) {
    return sbrk(size);
}

long getpagesize() {
    return sysconf(_SC_PAGESIZE);
}
#endif
#endif

} // namespace konan
