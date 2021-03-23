package com.woong.recogsound

import retrofit2.Call
import retrofit2.http.*


interface SoundRecogAPI {
    @POST("WiseASR/Recognition")
    fun transferSound(
        @Body access_key: String,
        @Body argument : Object,
        @Body language_code:String,
        @Body audio: String
    ): Call<return_object>
}




