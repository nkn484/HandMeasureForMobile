package com.resources.handmeasure.sdk.internal.ringsize

fun <T> List<T>.mergeDistinct(other: List<T>): List<T> {
    if (this.isEmpty()) return other.distinct()
    if (other.isEmpty()) return this.distinct()
    return (this + other).distinct()
}
