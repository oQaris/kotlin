/*
 * Copyright 2010-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#pragma once

#include <cstddef>
#include <new>
#include <utility>

namespace konan {
void* calloc(size_t count, size_t size);
void* calloc_aligned(size_t count, size_t size, size_t alignment);
void free(void* ptr);
} // namespace konan
