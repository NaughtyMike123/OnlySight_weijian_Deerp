package com.focusai.app.data.api

import com.google.gson.Gson
import retrofit2.HttpException

private val errorGson = Gson()

/** 从 HTTP 错误响应体解析 `error` 字段。 */
fun parsePremiumApiError(throwable: Throwable): String? {
    if (throwable !is HttpException) return null
    val body = throwable.response()?.errorBody()?.string().orEmpty()
    if (body.isBlank()) return null
    return runCatching {
        errorGson.fromJson(body, PremiumApiErrorResponse::class.java).error?.trim()
    }.getOrNull()?.takeIf { it.isNotBlank() }
}
