/*
 * Copyright 2010-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#include <cstdlib>

extern "C" void* konan_object_calloc(size_t count, size_t size, size_t alignment) {
    return ::calloc(count, size);
}

extern "C" void konan_object_free(void* ptr) {
    return ::free(ptr);
}
