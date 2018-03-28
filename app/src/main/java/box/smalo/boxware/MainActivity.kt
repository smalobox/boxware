package box.smalo.boxware

import android.app.Activity
import android.os.Bundle
import android.os.SystemClock
import android.view.Window
import android.view.WindowManager
import com.google.android.things.contrib.driver.pwmservo.Servo
import kotlinx.android.synthetic.main.activity_main.*
import org.ethereum.geth.*
import java.io.File
import java.math.BigInteger
import java.math.BigInteger.ZERO

class MainActivity : Activity() {

    private val keyStoreFile by lazy { File(filesDir, "keystore") }
    private val keyStore by lazy { KeyStore(keyStoreFile.absolutePath, Geth.LightScryptN, Geth.LightScryptP) }

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_main)

        main_text.text = getIPAddress(true)

        if (!State.hasInitialAccount) {
            keyStore.newAccount(DEFAULT_PASSWORD)
            State.hasInitialAccount = true
        }

        qr_display.setQRCode("ethereum:" + keyStore.accounts[0].address.hex)

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

            while (running) {
                SystemClock.sleep(3000)
                val currentBlock = ethereumNode.ethereumClient.syncProgress(ethereumContext)?.currentBlock
                val highestBlock = ethereumNode.ethereumClient.syncProgress(ethereumContext)?.highestBlock
                if (lastBlock != currentBlock) {
                    lastBlock = currentBlock
                }

                round++

                val balanceBigInt = ethereumNode.ethereumClient.getBalanceAt(ethereumContext, keyStore.accounts[0].address, currentNewHead)
                val balance = BigInteger(balanceBigInt.string())

                if (lastBalance != balance) {
                    toggleServo()
                    lastBalance = balance
                }

                runOnUiThread {
                    val text = "IP:" + getIPAddress(true) + "\nBlock: " + currentNewHead + "/" + (highestBlock
                            ?: currentNewHead) + "\nPeers:" + ethereumNode.peersInfo.size() + "\nBalance:" + balance + "\nround:$round"
                    node_text.text = text
                }
            }

            ethereumNode.stop()
        }).start()
    }

    private fun toggleServo() {
        if (servo.angle > 0) {
            servo.angle = 0.0
        } else {
            servo.angle = 180.0
        }
    }
}
