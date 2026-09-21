package com.click.lightmemo.network

import java.io.IOException
import java.net.ConnectException
import java.net.ProtocolException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Converts low-level client failures into messages that tell the user what to try next. */
internal fun userFacingRecognitionError(error: Throwable): String {
    if (error is RecognitionException) {
        return error.message ?: "识别失败，请稍后重试"
    }

    val cause = generateSequence(error) { it.cause }
        .firstOrNull { it is UnknownHostException ||
            it is SocketTimeoutException ||
            it is SSLException ||
            it is ProtocolException ||
            it is ConnectException ||
            it is SocketException ||
            it is IOException
        }
        ?: error

    return when (cause) {
        is UnknownHostException -> "找不到识别服务：请检查网络或 Base URL 是否正确。"
        is SocketTimeoutException -> "识别服务响应超时：请检查网络，或稍后重试。"
        is SSLException -> "安全连接失败：请检查设备时间、证书或网络代理。"
        is ProtocolException -> "识别接口协议不兼容：请检查 Base URL 是否为 OpenAI 兼容接口。"
        is ConnectException -> "无法连接识别服务：请检查网络、代理和 Base URL。"
        is SocketException -> "网络连接被中断：请切换网络、关闭代理后重试。"
        is IOException -> "识别服务连接失败：请检查网络、代理和 Base URL 后重试。"
        else -> error.message?.takeIf { it.isNotBlank() } ?: "识别失败，请稍后重试。"
    }
}

internal fun recognitionHttpErrorMessage(code: Int): String = when (code) {
    400 -> "识别服务不接受当前请求：请检查模型和接口配置。"
    401 -> "识别服务鉴权失败：请检查 API Key 是否正确。"
    403 -> "识别服务拒绝请求：请检查 API Key 权限或账户状态。"
    404 -> "找不到识别接口：请检查 Base URL 是否填写正确。"
    408 -> "识别服务请求超时，请稍后重试。"
    413 -> "图片或请求内容过大，请换一张更小的图片。"
    429 -> "识别请求过于频繁或额度不足，请稍后重试并检查账户状态。"
    in 500..599 -> "识别服务暂时不可用（HTTP $code），请稍后重试。"
    else -> "识别服务返回异常（HTTP $code），请检查接口配置。"
}
