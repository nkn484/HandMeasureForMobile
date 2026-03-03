package com.resources.handqualitygate.ringsize

enum class MeasurementFailReason {
    CARD_NOT_FOUND,
    SCALE_FAIL,
    HAND_NOT_FOUND,
    HAND_LOW_CONF,
    HAND_NOT_STABLE,
    WIDTH_FAIL,
    NOT_ENOUGH_VALID_FRAMES,
    NOT_ENOUGH_STABLE_FRAMES,
}

fun List<MeasurementFailReason>.asReasonStrings(): List<String> = map { it.name }
