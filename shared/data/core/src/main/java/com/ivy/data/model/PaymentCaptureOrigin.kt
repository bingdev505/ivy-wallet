package com.ivy.data.model

object PaymentCaptureOrigin {
    // Release and debug installations can otherwise listen to each other's alerts.
    fun isIvyPackage(packageName: String): Boolean = packageName == "com.ivy.wallet" || packageName.startsWith("com.ivy.wallet.")
    private val footer = Regex("(?i)(?:\\s+Review before recording in Ivy Wallet\\.)+$")
    fun originalBody(body: String): String = footer.replace(body.trim(), "").trim()
    fun isGeneratedAlert(body: String): Boolean = originalBody(body) != body.trim()
}
