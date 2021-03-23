package com.woong.recogsound

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.util.Base64
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.woong.recogsound.databinding.ActivityMainBinding
import org.apache.commons.lang3.StringEscapeUtils
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import kotlin.experimental.and

class MainActivity : AppCompatActivity() {


    private val PREFS_NAME : String = "prefs"
    private val MSG_KEY : String = "status"

    val API_Key = "1bb93bd5-ba32-418d-a3ec-1e37194b4adb"
    val BASE_URL = "http://aiopen.etri.re.kr:8000/"

    var curMode: String? = ""
    var result : String? = ""

    private val maxLenSpeech:Int = 16000*45
    var speechData = ByteArray(maxLenSpeech * 2)
    var lenSpeech:Int = 0
    var isRecording:Boolean = false
    var forceStop:Boolean = false


    private var mBinding: ActivityMainBinding? = null
    private val binding get() = mBinding!!

    private val handler = Handler{

        val bd:Bundle = it.data
        val v :String? = bd.getString(MSG_KEY)
        when (it.what){
            1 -> {
                binding.tvSoundtrans.text = v
                binding.btStart.text = "멈춤"
            }
            2 -> {
                binding.tvSoundtrans.text = v
                binding.btStart.isEnabled = false
            }
            3 -> {
                binding.tvSoundtrans.text = v
                binding.btStart.text = "시작"
            }
            4 -> {
                binding.tvSoundtrans.text = v
                binding.btStart.isEnabled = true
                binding.btStart.text = "시작"
            }
            5 -> {
                binding.tvSoundtrans.text = StringEscapeUtils.unescapeJava(result)
                binding.btStart.isEnabled = true
                binding.btStart.text = "시작"
            }

        }
        true
    }

    private fun SendMessage(str: String, id: Int){

        var msg: Message = handler.obtainMessage()
        var bd :Bundle = Bundle()
        bd.putString(MSG_KEY, str)
        msg.what = id
        msg.data = bd
        handler.sendMessage(msg)


    }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        mBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)



       /* var audioContents : String = ""
        var argumnet : Map<String, String>*/





       binding.btStart.setOnClickListener{
           if(isRecording){
               forceStop = true
           }
           else{
               try{
                   Thread(Runnable {
                       SendMessage("Recording..", 1)
                       try {
                           recordSpeech()
                           SendMessage("Recognizing...", 2)
                       } catch (e: RuntimeException) {
                           e.message?.let { it1 -> SendMessage(it1, 3) }
                           return@Runnable
                       }

                       val threadRecog = Thread {
                           sendDataAndGetResult()
                       }

                       threadRecog.start()
                       try {
                           threadRecog.join(20000)
                           if (threadRecog.isAlive) {
                               threadRecog.interrupt()
                               SendMessage("No response", 4)
                           } else {
                               SendMessage("OK", 5)
                           }
                       } catch (e: InterruptedException) {
                           SendMessage("Interrupted", 4)
                       }

                   }).start()

               } catch (t:Throwable){
                   binding.tvSoundtrans.text = t.toString()
                   forceStop = false
                   isRecording = false
               }
           }

       }




    }

    fun readStream(input: InputStream): String {

        var sb:StringBuilder = StringBuilder()
        var r :BufferedReader = BufferedReader(InputStreamReader(input), 1000)
        var line : String = r.readLine()

        while(line!=null){
            sb.append(line)
            line = r.readLine()
        }
        input.close()
        return sb.toString()

    }

    private fun recordSpeech(){
        try{
            var bufferSize:Int = AudioRecord.getMinBufferSize(16000,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT)
            var audio:AudioRecord= AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    16000,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize)

            lenSpeech =0
            if(audio.state != AudioRecord.STATE_INITIALIZED){
                throw RuntimeException("ERROR: Failed to initialize audio deevide.Allow app to access microphone")

            }
            else{
                var inBuffer = ShortArray(bufferSize)
                forceStop = false
                isRecording = true
                audio.startRecording()
                while(!forceStop){
                    var ret:Int = audio.read(inBuffer, 0, bufferSize)
                    for(i in 0 until ret){
                        if(lenSpeech >= maxLenSpeech){
                            forceStop = true
                            break
                        }

                        speechData[lenSpeech * 2] = inBuffer[i].toByte() and 0x00FF.toByte()
                        speechData[lenSpeech * 2 + 1] = ((inBuffer[i].toInt() and 0xFF00) shr 8).toByte()
                        lenSpeech++
                    }
                }

                audio.stop()
                audio.release()
                isRecording = false

            }
        } catch (e: Exception){
            throw RuntimeException(e.toString())
        }
    }

    private fun sendDataAndGetResult(){
        val openApiURL = "http://aiopen.etri.re.kr:8000/WiseASR/Recognition"
        val accessKey: String = API_Key

        //var gson = Gson()

        val languageCode: String = "korean"

        var request:HashMap<String, Any> = HashMap()
        var argument:HashMap<String, String> = HashMap()

        val audioContents: String = Base64.encodeToString(
                speechData, 0, lenSpeech*2, Base64.NO_WRAP);
        argument["language_code"] = languageCode
        argument["audio"] = audioContents

        request["access_key"] = accessKey
        request["argument"] = argument

        val soundRecogBody = SoundRecogBody(accessKey, argument, languageCode, audioContents)


         val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        val api = retrofit.create(SoundRecogAPI::class.java)
        val callSoundRecog = api.transferSound(soundRecogBody)

        callSoundRecog.enqueue(object : Callback<ReturnObject> {
            override fun onResponse(
                    call: Call<ReturnObject>,
                    response: Response<ReturnObject>
            ) {
                Log.d("main", "성공 : ${response.raw()}")


            }

            override fun onFailure(call: Call<ReturnObject>, t: Throwable) {
                Log.d("main", "실패 : $t")
            }
        })

    }

    override fun onDestroy() {
        // onDestroy 에서 binding class 인스턴스 참조를 정리해주어야 한다.
        mBinding = null
        super.onDestroy()
    }
}