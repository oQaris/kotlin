/*
 * Copyright 2010-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#pragma once

#include <cstddef>
#include <new>
#include <utility>

#include "Alignment.hpp"

namespace konan {
void* calloc(size_t count, size_t size);
void* calloc_aligned(size_t count, size_t size, size_t alignment);
void free(void* ptr);
} // namespace konan

namespace kotlin {

void* allocateInObjectSpace(size_t count, size_t size, size_t alignment) noexcept;
void deallocateInObjectSpace(void* ptr) noexcept;

template <typename T>
struct ObjectSpaceAllocator {
    using value_type = T;
    using size_type = std::size_t;
    using difference_type = std::ptrdiff_t;
    using propagate_on_container_move_assignment = std::true_type;
    using is_always_equal = std::true_type;

    ObjectSpaceAllocator() noexcept = default;

    ObjectSpaceAllocator(const ObjectSpaceAllocator&) noexcept = default;

    template <typename U>
    ObjectSpaceAllocator(const ObjectSpaceAllocator<U>&) noexcept {}

    T* allocate(std::size_t n) noexcept { return static_cast<T*>(allocateInObjectSpace(n, sizeof(T), kObjectAlignment)); }

    void deallocate(T* p, std::size_t n) noexcept { deallocateInObjectSpace(p); }
};

template <typename T, typename U>
bool operator==(const ObjectSpaceAllocator<T>&, const ObjectSpaceAllocator<U>&) noexcept {
    return true;
}

template <typename T, typename U>
bool operator!=(const ObjectSpaceAllocator<T>&, const ObjectSpaceAllocator<U>&) noexcept {
    return false;
}

} // namespace kotlin
