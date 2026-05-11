package com.devsphere.leafbloom.data.repository

sealed class WeatherError : Exception() {
    object NoNetwork : WeatherError() {
        private fun readResolve(): Any = NoNetwork
    }

    object RateLimited : WeatherError() {
        private fun readResolve(): Any = RateLimited
    }

    data class Server(val code: Int) : WeatherError() {
        override val message: String get() = "Server error: $code"
    }

    data class Unknown(override val cause: Throwable) : WeatherError() {
        override val message: String? get() = cause.message
    }

    companion object {
        fun fromHttp(code: Int): WeatherError =
            if (code == 429) RateLimited else Server(code)
    }
}
