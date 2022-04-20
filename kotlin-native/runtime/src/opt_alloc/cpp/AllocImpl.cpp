/*
 * Copyright 2010-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#include <cstddef>

// TODO: Just include header from mimalloc.
extern "C" {
void mi_free(void*);
void* mi_calloc_aligned(size_t count, size_t size, size_t alignment);
}

extern "C" void* konan_object_calloc(size_t count, size_t size, size_t alignment) {
    return ::mi_calloc_aligned(count, size, alignment);
}

extern "C" void konan_object_free(void* ptr) {
    return ::mi_free(ptr);
}
