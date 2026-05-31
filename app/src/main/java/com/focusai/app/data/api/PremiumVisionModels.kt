package com.focusai.app.data.api

import com.google.gson.annotations.SerializedName

data class PremiumVisionRequest(
    @SerializedName("app_token") val appToken: String,
    @SerializedName("image_base64") val imageBase64: String
)
