package com.rudderstack.integration.kotlin.adjust

import android.app.Activity
import android.app.Application
import androidx.annotation.VisibleForTesting
import com.adjust.sdk.Adjust
import com.adjust.sdk.AdjustAttribution
import com.adjust.sdk.AdjustConfig
import com.adjust.sdk.AdjustEvent
import com.adjust.sdk.AdjustInstance
import com.adjust.sdk.LogLevel
import com.rudderstack.sdk.kotlin.android.plugins.devicemode.IntegrationPlugin
import com.rudderstack.sdk.kotlin.android.plugins.devicemode.StandardIntegration
import com.rudderstack.sdk.kotlin.android.plugins.lifecyclemanagment.ActivityLifecycleObserver
import com.rudderstack.sdk.kotlin.android.utils.addLifecycleObserver
import com.rudderstack.sdk.kotlin.android.utils.application
import com.rudderstack.sdk.kotlin.core.Analytics
import com.rudderstack.sdk.kotlin.core.internals.logger.Logger
import com.rudderstack.sdk.kotlin.core.internals.logger.LoggerAnalytics
import com.rudderstack.sdk.kotlin.core.internals.models.Event
import com.rudderstack.sdk.kotlin.core.internals.models.IdentifyEvent
import com.rudderstack.sdk.kotlin.core.internals.models.TrackEvent
import com.rudderstack.sdk.kotlin.core.internals.utils.InternalRudderApi
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.rudderstack.integration.kotlin.adjust.AdjustConfig as AdjustDestinationConfig
import com.rudderstack.sdk.kotlin.android.Analytics as AndroidAnalytics

private const val ANONYMOUS_ID = "anonymousId"

private const val USER_ID = "userId"
private const val ADJUST_KEY = "Adjust"

// Install Attribution constants
private const val PROVIDER = "provider"
private const val TRACKER_TOKEN = "trackerToken"
private const val TRACKER_NAME = "trackerName"
private const val CAMPAIGN = "campaign"
private const val SOURCE = "source"
private const val NAME = "name"
private const val CONTENT = "content"
private const val AD_CREATIVE = "adCreative"
private const val AD_GROUP = "adGroup"
private const val INSTALL_ATTRIBUTED_EVENT = "Install Attributed"

/**
 * AdjustIntegration is a plugin that sends events to the Adjust SDK.
 */
@OptIn(InternalRudderApi::class)
class AdjustIntegration : StandardIntegration, IntegrationPlugin(), ActivityLifecycleObserver {

    override val key: String
        get() = ADJUST_KEY

    private var adjustInstance: AdjustInstance? = null

    private lateinit var eventToTokenMappings: List<EventToTokenMapping>
    private var enableInstallAttributionTracking: Boolean = false

    public override fun create(destinationConfig: JsonObject) {
        adjustInstance ?: run {
            destinationConfig.parseConfig<AdjustDestinationConfig>()?.let { config ->
                eventToTokenMappings = config.eventToTokenMappings
                enableInstallAttributionTracking = config.enableInstallAttributionTracking
                adjustInstance = initAdjust(
                    application = analytics.application,
                    appToken = config.appToken,
                    logLevel = LoggerAnalytics.logLevel,
                    enableInstallAttributionTracking = enableInstallAttributionTracking,
                    analytics = analytics
                )
                (analytics as? AndroidAnalytics)?.addLifecycleObserver(this)
                LoggerAnalytics.verbose("AdjustIntegration: Adjust SDK initialized.")
            }
        }
    }

    override fun getDestinationInstance(): Any? {
        return adjustInstance
    }

    override fun update(destinationConfig: JsonObject) {
        destinationConfig.parseConfig<AdjustDestinationConfig>()?.let { updatedConfig ->
            this.eventToTokenMappings = updatedConfig.eventToTokenMappings
        }
    }

    override fun identify(payload: IdentifyEvent) {
        payload.setSessionParams()
    }

    override fun track(payload: TrackEvent) {
        eventToTokenMappings.getTokenOrNull(payload.event)?.let { eventToken ->
            payload.setSessionParams()
            val adjustEvent = initAdjustEvent(eventToken).apply {
                addCallbackParameter(payload.properties)
                setRevenue(payload.properties)
                addCallbackParameter(payload.context.toJsonObject(PropertiesConstants.TRAITS))
            }
            Adjust.trackEvent(adjustEvent)
            LoggerAnalytics.verbose("AdjustIntegration: Track event sent to Adjust.")
        } ?: run {
            LoggerAnalytics.error(
                "AdjustIntegration: Either Event to Token mapping is not configured in the dashboard " +
                    "or the corresponding token is empty. Therefore dropping the ${payload.event} event."
            )
        }
    }

    override fun reset() {
        Adjust.removeGlobalPartnerParameters()
        LoggerAnalytics.verbose("AdjustIntegration: Reset call completed.")
    }

    private fun Event.setSessionParams() {
        Adjust.addGlobalPartnerParameter(ANONYMOUS_ID, anonymousId)
        if (userId.isNotBlank()) {
            Adjust.addGlobalPartnerParameter(USER_ID, userId)
        }
    }

    private fun AdjustEvent.addCallbackParameter(jsonObject: JsonObject) {
        jsonObject.keys.forEach { key ->
            addCallbackParameter(key, jsonObject.getStringOrNull(key))
        }
    }

    private fun AdjustEvent.setRevenue(jsonObject: JsonObject) {
        jsonObject.getDoubleOrNull(PropertiesConstants.REVENUE)?.let { revenue ->
            jsonObject.getStringOrNull(PropertiesConstants.CURRENCY)?.let { currency ->
                setRevenue(revenue, currency)
            }
        }
    }

    override fun onActivityResumed(activity: Activity) {
        Adjust.onResume()
    }

    override fun onActivityPaused(activity: Activity) {
        Adjust.onPause()
    }
}

private fun initAdjust(
    application: Application,
    appToken: String,
    logLevel: Logger.LogLevel,
    enableInstallAttributionTracking: Boolean,
    analytics: Analytics
): AdjustInstance {
    val adjustEnvironment = getAdjustEnvironment(logLevel)
    val adjustConfig = initAdjustConfig(application, appToken, adjustEnvironment)
        .apply {
            setLogLevel(logLevel)
            setAllListeners(enableInstallAttributionTracking, analytics)
            enableSendingInBackground()
        }
    Adjust.initSdk(adjustConfig)
    return Adjust.getDefaultInstance()
}

private fun getAdjustEnvironment(logLevel: Logger.LogLevel): String {
    return if (logLevel != Logger.LogLevel.NONE) {
        AdjustConfig.ENVIRONMENT_SANDBOX
    } else {
        AdjustConfig.ENVIRONMENT_PRODUCTION
    }
}

@VisibleForTesting
internal fun initAdjustConfig(application: Application, appToken: String, adjustEnvironment: String) =
    AdjustConfig(application, appToken, adjustEnvironment)

private fun AdjustConfig.setLogLevel(logLevel: Logger.LogLevel) {
    when (logLevel) {
        Logger.LogLevel.VERBOSE -> setLogLevel(LogLevel.VERBOSE)
        Logger.LogLevel.DEBUG -> setLogLevel(LogLevel.DEBUG)
        Logger.LogLevel.INFO -> setLogLevel(LogLevel.INFO)
        Logger.LogLevel.WARN -> setLogLevel(LogLevel.WARN)
        Logger.LogLevel.ERROR -> setLogLevel(LogLevel.ERROR)
        Logger.LogLevel.NONE -> setLogLevel(LogLevel.SUPPRESS)
    }
}

private fun AdjustConfig.setAllListeners(enableInstallAttributionTracking: Boolean, analytics: Analytics) {
    setOnAttributionChangedListener { attribution ->
        LoggerAnalytics.debug("Adjust: Attribution callback called!")
        LoggerAnalytics.debug("Adjust: Attribution: $attribution")

        if (enableInstallAttributionTracking) {
            sendInstallAttributedEvent(analytics, attribution)
        }
    }
    setOnEventTrackingSucceededListener { adjustEventSuccess ->
        LoggerAnalytics.debug("Adjust: Event success callback called!")
        LoggerAnalytics.debug("Adjust: Event success data: $adjustEventSuccess")
    }
    setOnEventTrackingFailedListener { adjustEventFailure ->
        LoggerAnalytics.debug("Adjust: Event failure callback called!")
        LoggerAnalytics.debug("Adjust: Event failure data: $adjustEventFailure")
    }
    setOnSessionTrackingSucceededListener { adjustSessionSuccess ->
        LoggerAnalytics.debug("Adjust: Session success callback called!")
        LoggerAnalytics.debug("Adjust: Session success data: $adjustSessionSuccess")
    }
    setOnSessionTrackingFailedListener { adjustSessionFailure ->
        LoggerAnalytics.debug("Adjust: Session failure callback called!")
        LoggerAnalytics.debug("Adjust: Session failure data: $adjustSessionFailure")
    }
    setOnDeferredDeeplinkResponseListener { deeplink ->
        LoggerAnalytics.debug("Adjust: Deferred deep link callback called!")
        LoggerAnalytics.debug("Adjust: Deep link URL: $deeplink")
        true
    }
}

@VisibleForTesting
internal fun initAdjustEvent(eventToken: String) = AdjustEvent(eventToken)

/**
 * Sends an "Install Attributed" event to RudderStack when attribution data is received from Adjust.
 */
private fun sendInstallAttributedEvent(analytics: Analytics, attribution: AdjustAttribution) {
    val properties = buildJsonObject {
        put(PROVIDER, ADJUST_KEY)

        // Add attribution properties with null checks
        putIfNotNull(TRACKER_TOKEN, attribution.trackerToken)
        putIfNotNull(TRACKER_NAME, attribution.trackerName)

        // Create campaign object with attribution data
        val campaign = buildJsonObject {
            putIfNotNull(SOURCE, attribution.network)
            putIfNotNull(NAME, attribution.campaign)
            putIfNotNull(CONTENT, attribution.clickLabel)
            putIfNotNull(AD_CREATIVE, attribution.creative)
            putIfNotNull(AD_GROUP, attribution.adgroup)
        }

        if (campaign.isNotEmpty()) {
            put(CAMPAIGN, campaign)
        }
    }

    analytics.track(INSTALL_ATTRIBUTED_EVENT, properties)
    LoggerAnalytics.info("AdjustIntegration: Install Attributed event sent successfully with properties: $properties")
}
