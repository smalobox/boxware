package box.smalo.boxware

import android.app.Activity
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.Window
import android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN
import com.google.android.things.contrib.driver.pwmservo.Servo
import kotlinx.android.synthetic.main.activity_main.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.kethereum.keccakshortcut.keccak
import org.walleth.khex.hexToByteArray
import org.walleth.khex.toHexString
import org.walleth.khex.toNoPrefixHexString
import java.math.BigInteger

val TOPIC_RENTED = "Rented(address,address,uint256,uint256)".toByteArray().keccak().toHexString()
val TOPIC_OPENED = "Opened(address,address)".toByteArray().keccak().toHexString()
val TOPIC_CLOSED = "Closed(address,address)".toByteArray().keccak().toHexString()

//val contractAddress = "0x2f6bd3a816f4de987b89c3dc21e1a56097f64fa3"
val contractAddress = "0x792768719651e03e4db560ad02e45beaa05f1139"
class MainActivity : Activity() {

    var isBoxOpen = false

    private val servo by lazy {
        Servo("PWM0").apply {
            setAngleRange(0.0, 180.0)
            setEnabled(true)
        }
    }

    private var rentedUntil: Long? = null
    var round = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(FLAG_FULLSCREEN, FLAG_FULLSCREEN);

        Log.i("GotLog", "RentSig:" + TOPIC_RENTED)
        Log.i("GotLog", "OpenSig:" + TOPIC_OPENED)
        Log.i("GotLog", "CloseSig:" + TOPIC_CLOSED)

        setContentView(R.layout.activity_main)

        main_text.text = getIPAddress(true)

        val okhttp_client = OkHttpClient.Builder().build()

        Thread(Runnable {
            while (true) {
                try {

                    val url = "https://rinkeby.etherscan.io/api?module=logs&action=getLogs&fromBlock=" + (State.lastProcessedBlock + 1L) + "&toBlock=latest&address=$contractAddress"
                    val json = okhttp_client.newCall(Request.Builder().url(url).build()).execute().body()?.string()


                    val jsonArray = JSONObject(json).getJSONArray("result")
                    Log.i("round", "result size " + jsonArray.length())

                    (0 until jsonArray.length()).map { jsonArray.getJSONObject(it) }.forEach {
                        val newBlockNumber = BigInteger(it.getString("blockNumber").replace("0x", ""), 16)
                        val topics = it.getJSONArray("topics")
                        val data = it.getString("data")
                        (0 until topics.length()).map { topics.getString(it) }.forEach {
                            when (it) {
                                TOPIC_RENTED -> {
                                    Log.i("round", "Got - RENTED with data $data")
                                    val time = BigInteger(data.hexToByteArray().toList().subList(0, 32).toNoPrefixHexString(), 16)
                                    Log.i("round", "Got - RENTED with time $time")

                                    rentedUntil = System.currentTimeMillis() + time.toLong() * 60 * 1000

                                }
                                TOPIC_OPENED -> {
                                    Log.i("round", "Got - OPENED")
                                    isBoxOpen = true
                                }

                                TOPIC_CLOSED -> {
                                    Log.i("round", "Got - CLOSED")
                                    isBoxOpen = false
                                }
                            }
                        }
                        if (newBlockNumber > BigInteger(State.lastProcessedBlock.toString())) {
                            State.lastProcessedBlock = newBlockNumber.toLong()
                            Log.i("round", "new Block " + newBlockNumber.toLong())
                        }

                    }
                } catch (e: Exception) {
                    Log.e("round", "error", e)
                }
                runOnUiThread {
                    updateText()
                }
                SystemClock.sleep(1000)
                round++
            }
        }).start()
    }

    private fun updateText() {
        val secondsLeft = secondsLeft()
        var text = "Last action Block: " + State.lastProcessedBlock
        text += "\nRound: $round"
        text += "\nRented: " + if (secondsLeft > 0) {
            secondsLeft.toString() + "s"
        } else {
            "no"
        }
        if (secondsLeft < 0) {
            isBoxOpen = false
        }
        val newQR=when {
            isBoxOpen -> "ethereum:$contractAddress@4/close?value=0&gas=200000"
            (secondsLeft > 0) -> "ethereum:$contractAddress@4/open?value=0&gas=200000"
            else -> "ethereum:$contractAddress@4/rent?value=0.003e18&gas=200000"
        }
        qr_display.setQRCode(newQR)

        text += "\nOpen: " + if (isBoxOpen) "yes" else "no"
        node_text.text = text
        adjustServo()
    }

    private fun secondsLeft() = ((rentedUntil ?: 0) - System.currentTimeMillis()) / 1000

    private fun adjustServo() {
        if (isBoxOpen) {
            servo.angle = 180.0
        } else {
            servo.angle = 0.0
        }
    }
}
