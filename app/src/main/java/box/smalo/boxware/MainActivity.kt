package box.smalo.boxware

import android.app.Activity
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.Window
import android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN
import com.google.android.things.contrib.driver.pwmservo.Servo
import kotlinx.android.synthetic.main.activity_main.*
import org.ethereum.geth.*
import org.kethereum.keccakshortcut.keccak
import org.walleth.khex.toHexString
import org.walleth.khex.toNoPrefixHexString
import java.io.File
import java.math.BigInteger
import java.math.BigInteger.ZERO

val TOPIC_RENTED ="Rented(address,address,uint256,uint256)".toByteArray().keccak().toHexString()
val TOPIC_OPENED ="Opened(address,address)".toByteArray().keccak().toHexString()
val TOPIC_CLOSED ="Returned(address,address)".toByteArray().keccak().toHexString()

val contractAddress = "0x2f6bd3a816f4de987b89c3dc21e1a56097f64fa3"

class MainActivity : Activity() {

    private val keyStoreFile by lazy { File(filesDir, "keystore") }
    private val keyStore by lazy { KeyStore(keyStoreFile.absolutePath, Geth.LightScryptN, Geth.LightScryptP) }

    private var rentedUntil :Long? = null

    private enum class Network {
        RINKEBY, MAIN, ROPSTEN
    }

    private var currentNet = Network.RINKEBY

    private val servo by lazy {
        Servo("PWM0").apply {
            setAngleRange(0.0, 180.0)
            setEnabled(true)
        }
    }

    private val nodeConfig by lazy {
        NodeConfig().apply {
            when (currentNet) {
                Network.RINKEBY -> {
                    ethereumNetworkID = 4L
                    ethereumGenesis = Geth.rinkebyGenesis()

                    ethereumNetStats = "smartLockBox:Respect my authoritah!@stats.rinkeby.io"
                }
                Network.MAIN -> {
                    ethereumGenesis = Geth.mainnetGenesis()
                }

                Network.ROPSTEN -> {
                    ethereumNetworkID = 3L
                    ethereumGenesis = Geth.testnetGenesis()
                }

            }
        }
    }

    private val ethereumContext by lazy { Context() }
    private val ethereumNode by lazy {
        Geth.newNode(cacheDir.absolutePath + "/ethereum4_" + currentNet.toString().toLowerCase(), nodeConfig)
    }

    private val startNode = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(FLAG_FULLSCREEN, FLAG_FULLSCREEN);

        Log.i("GotLog", "RentSig:"+TOPIC_RENTED)
        Log.i("GotLog", "OpenSig:"+ TOPIC_OPENED)
        Log.i("GotLog", "CloseSig:"+ TOPIC_CLOSED)

        setContentView(R.layout.activity_main)

        main_text.text = getIPAddress(true)

        if (startNode) {
            main_text.text = "starting:"+getIPAddress(true)
            if (!State.hasInitialAccount) {
                keyStore.newAccount(DEFAULT_PASSWORD)
                State.hasInitialAccount = true
            }

            qr_display.setQRCode("ethereum:$contractAddress@4/rent?value=0.001e18&gas=200000")
            //qr_display.setQRCode("ethereum:" + keyStore.accounts[0].address.hex)

            var currentNewHead = 0L
            var running = true
            var round = 1
            var lastBalance = ZERO


            Thread(Runnable {
                ethereumNode.start()

                ethereumNode.ethereumClient.subscribeNewHead(ethereumContext, object : NewHeadHandler {
                    override fun onError(p0: String?) {
                    }

                    override fun onNewHead(p0: Header) {
                        currentNewHead = p0.number
                    }

                }, 16)

                var lastBlock = ethereumNode.ethereumClient.syncProgress(ethereumContext)?.highestBlock

                class MyHandler : FilterLogsHandler {
                    override fun onFilterLogs(p0: org.ethereum.geth.Log?) {
                        Log.i("Tag", "GotLog1" + p0.toString())
                        Log.i("Tag", "GotLog-h" + p0!!.address.hex)
                        Log.i("Tag", "GotLog-d" + p0.data.toList().toHexString())
                        (0..p0.topics.size()).forEach {
                            if (TOPIC_RENTED.toLowerCase()== p0.topics[it].hex) {
                                val toNoPrefixHexString = BigInteger(p0.data.toList().subList(0, 32).toNoPrefixHexString(),16)
                                Log.i("Tag", "GotLog-RENTED" + toNoPrefixHexString + "mins")

                                rentedUntil = System.currentTimeMillis() + toNoPrefixHexString.toLong()*60*1000

                            }
                            Log.i("Tag", "GotLog-t" + it + p0.topics[it].hex)
                        }
                    }

                    override fun onError(p0: String?) {
                    }
                }

                val query = FilterQuery().apply {
                    addresses = Geth.newAddressesEmpty().apply { append(Geth.newAddressFromHex(contractAddress)) }
                }

                ethereumNode.ethereumClient.subscribeFilterLogs(ethereumContext, query, MyHandler(), 0L)

                while (running) {
                    SystemClock.sleep(1000)
                    val currentBlock = ethereumNode.ethereumClient.syncProgress(ethereumContext)?.currentBlock
                    val highestBlock = ethereumNode.ethereumClient.syncProgress(ethereumContext)?.highestBlock
                    if (lastBlock != currentBlock) {
                        lastBlock = currentBlock
                    }

                    round++

                    //val balanceBigInt = ethereumNode.ethereumClient.getBalanceAt(ethereumContext, keyStore.accounts[0].address, currentNewHead)
                    //val balance = BigInteger(balanceBigInt.string())

                    /*if (lastBalance != balance) {
                    toggleServo()
                    lastBalance = balance
                }*/

                    runOnUiThread {

                        val secondsLeft=((rentedUntil?:0)-System.currentTimeMillis())/1000
                        var text ="Block: " + currentNewHead + "/" + (highestBlock
                                ?: currentNewHead) + "\nPeers:" + ethereumNode.peersInfo.size() + " - Tick:$round\nid=" + BuildConfig.APPLICATION_ID
                        text+="\nrented: " + if (secondsLeft>0) {
                            secondsLeft.toString()+"s"
                        } else {
                            "no"
                        }

                        node_text.text = text
                    }
                }

                ethereumNode.stop()
            }).start()
        }
    }

    private fun toggleServo() {
        if (servo.angle > 0) {
            servo.angle = 0.0
        } else {
            servo.angle = 180.0
        }
    }
}
