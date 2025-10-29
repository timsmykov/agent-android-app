package com.example.aichat.core

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val throwable: Throwable) : Result<Nothing>()
    object Loading : Result<Nothing>()

    inline fun <R> fold(onSuccess: (T) -> R, onError: (Throwable) -> R, onLoading: () -> R): R = when (this) {
        is Success -> onSuccess(data)
        is Error -> onError(throwable)
        Loading -> onLoading()
    }
}
