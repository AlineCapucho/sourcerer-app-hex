package app.infrastructure.analytics

import app.BuildConfig
import app.application.ports.AnalyticsPort
import com.github.kittinunf.fuel.core.FuelManager
import com.github.kittinunf.fuel.core.Method

/**
 * Adapter: Implements AnalyticsPort using Google Analytics Measurement Protocol.
 * This is an output adapter — sends tracking events to GA.
 */
class GoogleAnalyticsAdapter : AnalyticsPort {

    private val IS_ENABLED = BuildConfig.IS_GA_ENABLED
    private val BASE_URL = "/virtual/app/"
    private val PROTOCOL_VERSION = "1"
    private val TRACKING_ID = BuildConfig.GA_TRACKING_ID
    private val DATA_SOURCE = "app"
    private val HIT_PAGEVIEW = "pageview"

    private val fuelManager = FuelManager()

    var uuid: String = ""
    var username: String = ""

    init {
        fuelManager.basePath = BuildConfig.GA_BASE_PATH
    }

    override fun trackEvent(category: String, action: String, label: String) {
        if (!IS_ENABLED || (username.isEmpty() && uuid.isEmpty())) {
            return
        }

        val params = mutableListOf<Pair<String, String>>()
        params.add("v" to PROTOCOL_VERSION)
        params.add("tid" to TRACKING_ID)
        params.add("ds" to DATA_SOURCE)
        params.add("t" to HIT_PAGEVIEW)
        params.add("dp" to BASE_URL + action)

        if (uuid.isNotEmpty()) params.add("cid" to uuid)
        if (username.isNotEmpty()) params.add("uid" to username)

        try {
            fuelManager.request(Method.POST, "/collect", params).responseString()
        } catch (e: Throwable) {
            // Silently fail — analytics should not break the app
        }
    }
}
