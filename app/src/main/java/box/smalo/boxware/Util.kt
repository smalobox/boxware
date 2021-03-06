package box.smalo.boxware

import android.graphics.drawable.BitmapDrawable
import android.widget.ImageView
import net.glxn.qrgen.android.QRCode
import java.net.NetworkInterface
import java.util.*

fun ImageView.setQRCode(content: String) {
    val drawable = BitmapDrawable(resources, QRCode.from(content).bitmap())
    drawable.setAntiAlias(false)
    drawable.isFilterBitmap = false
    setImageDrawable(drawable)
}

fun getIPAddress(useIPv4: Boolean): String {
    try {
        val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
        for (currentInterface in interfaces) {
            val addresses = Collections.list(currentInterface.inetAddresses)
            for (address in addresses) {
                if (!address.isLoopbackAddress) {
                    val hostAddress = address.hostAddress
                    val isIPv4 = hostAddress.indexOf(':') < 0

                    if (useIPv4) {
                        if (isIPv4)
                            return hostAddress
                    } else {
                        if (!isIPv4) {
                            val delim = hostAddress.indexOf('%') // drop ip6 zone suffix
                            return if (delim < 0) hostAddress.toUpperCase() else hostAddress.substring(0, delim).toUpperCase()
                        }
                    }
                }
            }
        }
    } catch (ex: Exception) {
    }
    // for now eat exceptions
    return "no IP found"
}
