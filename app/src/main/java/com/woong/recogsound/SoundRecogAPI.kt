package com.woong.recogsound

import com.google.gson.JsonObject
import retrofit2.Call
import retrofit2.http.*

data class SoundRecogBody(
        val access_key:String,
        val argument : Any,
        val languate_code:String,
        val audio: String
)


interface SoundRecogAPI {
    @POST("WiseASR/Recognition")
    fun transferSound(
       @Body requestBody: SoundRecogBody
    ): Call<JsonObject>

}




