package com.pulse.bluetoothdisable.testaccess

enum class TestAccessStatus {
    UNCONFIGURED,
    CHECKING,
    ALLOWED,
    DENIED,
    EXPIRED,
    UNAVAILABLE,
    INVALID_TOKEN,
    STORAGE_ERROR,
}

data class TestAccessUiState(
    val supported: Boolean = false,
    val hasToken: Boolean = false,
    val busy: Boolean = false,
    val allowed: Boolean = false,
    val status: TestAccessStatus = TestAccessStatus.UNCONFIGURED,
)
