package com.rudderstack.sampleapp.analytics.customplugins

import com.rudderstack.sdk.kotlin.core.Analytics
import com.rudderstack.sdk.kotlin.core.internals.logger.LoggerAnalytics
import com.rudderstack.sdk.kotlin.core.internals.models.Event
import com.rudderstack.sdk.kotlin.core.internals.models.TrackEvent
import com.rudderstack.sdk.kotlin.core.internals.plugins.Plugin

/**
 * This plugin filters out specific analytics events from being processed in the analytics pipeline. 
 * It allows you to prevent certain events from being tracked or sent to destinations.
 * 
 * ## Usage:
 * ```kotlin
 * // Create and add the plugin
 * val eventFilteringPlugin = EventFilteringPlugin()
 * analytics.add(eventFilteringPlugin)
 * ```
 */
class EventFilteringPlugin : Plugin {

    override val pluginType: Plugin.PluginType = Plugin.PluginType.OnProcess
    override lateinit var analytics: Analytics

    // List of events to filter out - this can be modified according to the need
    private val eventsToFilter = mutableListOf("Application Opened", "Application Backgrounded")

    override suspend fun intercept(event: Event): Event? {
        if (event is TrackEvent && eventsToFilter.contains(event.event)) {
            LoggerAnalytics.verbose("EventFilteringPlugin: Event \"${event.event}\" is filtered out.")
            return null
        }
        return event
    }

    override fun teardown() {
        eventsToFilter.clear()
    }
}
