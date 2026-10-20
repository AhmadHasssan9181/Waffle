package com.noobdev.waffle.data.model

/**
 * Severity of an alert or hazard. Lives in the data layer so the model does not depend on the
 * design system; the UI maps each level to a colour in `design/components`.
 */
enum class StatusLevel {
    CRITICAL,
    WARNING,
    INFO,
    SAFE
}
