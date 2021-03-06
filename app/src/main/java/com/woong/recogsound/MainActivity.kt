package com.woong.recogsound

import android.content.Context
import android.content.SharedPreferences
import android.media.*
import android.media.AudioTrack
import android.os.*
import android.util.Base64
import android.util.Log
import android.widget.SeekBar
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.gson.JsonObject
import com.woong.recogsound.databinding.ActivityMainBinding
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.*
import kotlin.experimental.and

class MainActivity : AppCompatActivity() {


    private val msgKey : String = "status"
    private val apiKey = "1bb93bd5-ba32-418d-a3ec-1e37194b4adb"
    private val baseUrl = "http://aiopen.etri.re.kr:8000/"

    //변환 결과 저장 변수
    var result : String? = ""

    private val maxLenSpeech:Int = 16000*45
    var speechData = ByteArray(maxLenSpeech * 2)
    var lenSpeech:Int = 0
    var isRecording:Boolean = false
    var forceStop:Boolean = false

    lateinit var languageCode: String
    lateinit var audio:AudioRecord

    // Seekbar 데이터 저장변수
    private var progressValue:Int = 24


    var bufferSize:Int = AudioRecord.getMinBufferSize(16000,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT)

    //뷰바인딩 선언
    private var mBinding: ActivityMainBinding? = null
    private val binding get() = mBinding!!




    private val handler = Handler{

        val bd:Bundle = it.data
        val v :String? = bd.getString(msgKey)
        when (it.what){
            //녹음 시작
            1 -> {
                binding.tvSoundtrans.text = v
                binding.btStart.text = "멈춤"
            }
            // 녹음이 정상적으로 종료(버튼 또는 max time)
            2 -> {
                binding.tvSoundtrans.text = v
                binding.btStart.isEnabled = false
            }
            //녹음이 비정상적으로 종료(마이크 권한 등)
            3 -> {

                Toast.makeText(this, "앱 정보에서 마이크 권한을 허용한 후 다시 실행해주세요!!", Toast.LENGTH_LONG).show()
                binding.tvSoundtrans.text = v
                binding.btStart.text = "시작"
            }
            // 인식이 비정상적으로 종료(마이크 권한 등)
            4 -> {
                binding.tvSoundtrans.text = v
                binding.btStart.isEnabled = true
                binding.btStart.text = "시작"
            }
            // 인식이 정상적으로 종료(thread내에서 exception 포함)
            5 -> {
                binding.tvSoundtrans.text = result
                //binding.tvSoundtrans.text = result
                binding.btStart.isEnabled = true
                binding.btStart.text = "시작"
            }

        }
        true
    }

    private fun sendMsg(str: String, id: Int){

        val msg: Message = handler.obtainMessage()
        val bd :Bundle = Bundle()
        bd.putString(msgKey, str)
        msg.what = id
        msg.data = bd
        handler.sendMessage(msg)


    }


    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        mBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)


        //인텐트 받아오기(언어 선택)
        when {
            intent.hasExtra("kor") -> {
                languageCode = intent.getStringExtra("kor")
            }
            intent.hasExtra("eng") -> {
                languageCode = intent.getStringExtra("eng")
            }
            intent.hasExtra("jp") -> {
                languageCode = intent.getStringExtra("jp")
            }
            intent.hasExtra("ch") -> {
                languageCode = intent.getStringExtra("ch")
            }
            else -> {
                Log.e("Main", "가져온 데이터 없음")
            }
        }


        //광고 넣기
        MobileAds.initialize(this){}
        val adRequest = AdRequest.Builder().build()
        binding.adView.loadAd(adRequest)


        // SeekBar의 글자크기 데이터 불러오기
        val sf:SharedPreferences = getSharedPreferences("SeekbarData", Context.MODE_PRIVATE)
        val num:Int = sf.getInt("SeekNum", 24)

        binding.textsizeSeekbar.min = 15
        binding.textsizeSeekbar.max = 50
        binding.textsizeSeekbar.progress = num
        binding.tvSoundtrans.textSize = num.toFloat()

        //SeekBar 이벤트
        binding.textsizeSeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener{

            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.tvSoundtrans.textSize = progress.toFloat()
                progressValue = progress
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {

            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {

            }

        })


       binding.btStart.setOnClickListener{
           if(isRecording){
               forceStop = true
           }
           else{
               try{
                   Thread(Runnable {
                       sendMsg("듣고 있습니다.", 1)
                       try {
                           recordSpeech()
                           sendMsg("듣고 있습니다.", 2)
                       } catch (e: RuntimeException) {
                           //e.message?.let { it1 -> SendMessage(it1, 3) }
                           //SendMessage("앱 정보에서 마이크 권한을 허용한 후 다시 실행해주세요!!", 3)
                           sendMsg("", 3)



                           return@Runnable
                       }

                       val threadRecog = Thread {
                           result = sendDataAndGetResult()
                       }

                       threadRecog.start()
                       try {
                           threadRecog.join(20000)
                           if (threadRecog.isAlive) {
                               threadRecog.interrupt()
                               sendMsg("No response", 4)
                           } else {
                               sendMsg("OK", 5)
                           }
                       } catch (e: InterruptedException) {
                           sendMsg("Interrupted", 4)
                       }

                   }).start()

               } catch (t: Throwable){
                   binding.tvSoundtrans.text = t.toString()
                   forceStop = false
                   isRecording = false
               }
           }

       }




    }


    private fun recordSpeech(){
        try{

            if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
                bufferSize = 16000 * 2
            }

            audio = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    16000,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize)

            lenSpeech =0
            if(audio.state != AudioRecord.STATE_INITIALIZED){
                throw RuntimeException()

            }
            else{
                val inBuffer = ShortArray(bufferSize)
                forceStop = false
                isRecording = true
                audio.startRecording()


                while(!forceStop){
                    val ret:Int = audio.read(inBuffer, 0, bufferSize)
                    //player.write(inBuffer, 0 , inBuffer.size)
                    for(i in 0 until ret){
                        if(lenSpeech >= maxLenSpeech){
                            forceStop = true
                            break
                        }

                        speechData[lenSpeech * 2] = inBuffer[i].toByte() and 0x00FF.toByte()
                        speechData[lenSpeech * 2 + 1] = ((inBuffer[i].toInt() and 0xFF00) shr 8).toByte()
                        //speechData[lenSpeech * 2 + 1] = ((inBuffer[i].toInt()) shr 8).toByte()
                        //speechData[lenSpeech * 2 + 1] = ((inBuffer[i] and 0xFF00.toShort()).rotateRight(8)) as Byte
                        //bytes[(i * 2) + 1] = (byte) (sData[i] >> 8);

                        lenSpeech++
                    }


                }

                audio.stop()
                audio.release()
                isRecording = false

            }
        } catch (t: Throwable){
            throw RuntimeException(t.toString())
        }
    }


    //
    private fun sendDataAndGetResult() : String{

        val accessKey: String = apiKey.trim()
        val request: MutableMap<String, Any> = HashMap()
        val argument: MutableMap<String, String?> = HashMap()

        val audioContents = Base64.encodeToString(
                speechData, 0, lenSpeech * 2, Base64.NO_WRAP)

        argument["language_code"] = languageCode
        argument["audio"] = audioContents

        request["access_key"] = accessKey
        request["argument"] = argument




        //오류발생시 어떤 오류인지 확인 하고 싶을때
        /*val clientBuilder = OkHttpClient.Builder()
        val loggingInterceptor = HttpLoggingInterceptor()
        loggingInterceptor.level = HttpLoggingInterceptor.Level.BODY
        clientBuilder.addInterceptor(loggingInterceptor)*/


        //레트로핏을 사용하여 rest통신
        val soundRecogBody = SoundRecogBody(accessKey, argument, languageCode, audioContents)
         val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                 //.client(clientBuilder.build())// 오류발생시 어떤 오류인지 확인 하고 싶을때
                .build()
        val api = retrofit.create(SoundRecogAPI::class.java)
        val callSoundRecog = api.transferSound(soundRecogBody)


        //결과값을 json 파싱
        callSoundRecog.enqueue(object : Callback<JsonObject> {
            override fun onResponse(
                    call: Call<JsonObject>,
                    response: Response<JsonObject>
            ) {


                Log.d("main", "성공 : ${response.raw()}")
                Log.d("main", "성공 : ${response.body()}")

                val re: JsonObject? = response.body()
                if (re != null) {

                    val parseResult = response.body()?.get("return_object").toString()
                    val parseLength = parseResult.length

                    when (languageCode) {
                        "korean" -> {

                            if(parseResult.substring(15, parseLength-2)=="ASR_NOTOKEN"){
                                result = "다시 한번 말해주세요"
                            }
                            else
                            result = parseResult.substring(15, parseLength-2)
                        }
                        "english" -> {
                            if(parseResult.substring(15, parseLength-4)=="  ")
                            {
                                result = "Speak one more time"
                            }
                            else
                            result = parseResult.substring(15, parseLength-4)
                        }
                        "japanese" -> {
                            result = parseResult.substring(15, parseLength-2)
                        }
                        "chinese" -> {
                            result = parseResult.substring(15, parseLength-2)
                        }
                        else -> {
                            Log.d("Main", "언어데이터 없음")
                        }
                    }

                }

                binding.tvSoundtrans.text = result


            }

            override fun onFailure(call: Call<JsonObject>, t: Throwable) {
                Toast.makeText(this@MainActivity, "모바일 데이터 및 wifi 연결을 확인하세요!", Toast.LENGTH_LONG).show()
                Log.d("main", "실패 : $t")

            }
        })
        return """잠시만
            |기다려주세요...
        """.trimMargin()

    }

    override fun onStop() {
        super.onStop()

        //Seekbar 글자크기 데이터 저장
        val prefs :SharedPreferences = getSharedPreferences("SeekbarData", Context.MODE_PRIVATE)
        val editor : SharedPreferences.Editor = prefs.edit()
        editor.putInt("SeekNum", progressValue)
        editor.apply()
    }

    override fun onDestroy() {
        // onDestroy 에서 binding class 인스턴스 참조를 정리해주어야 한다.
        mBinding = null
        super.onDestroy()
    }
}