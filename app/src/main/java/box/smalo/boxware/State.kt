package box.smalo.boxware

import com.chibatching.kotpref.KotprefModel

object State : KotprefModel() {
    var hasInitialAccount by booleanPref(default = false)
}