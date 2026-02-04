package com.rudderstack.sampleapp.analytics.customplugins

import com.rudderstack.sdk.kotlin.core.internals.models.GroupEvent
import com.rudderstack.sdk.kotlin.core.internals.models.IdentifyEvent
import com.rudderstack.sdk.kotlin.core.internals.models.ScreenEvent
import com.rudderstack.sdk.kotlin.core.internals.models.TrackEvent
import com.rudderstack.sdk.kotlin.core.internals.models.emptyJsonObject
import com.rudderstack.sdk.kotlin.core.internals.models.useridentity.UserIdentity
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class EventFilteringPluginTest {

    private lateinit var eventFilteringPlugin: EventFilteringPlugin

    @BeforeEach
    fun setup() {
        eventFilteringPlugin = EventFilteringPlugin()
    }

    @Test
    fun `given a filtered track event, when intercepted, then event is filtered out and returns null`() = runTest {
        val filteredEvent = TrackEvent(event = "Application Opened", properties = emptyJsonObject)

        val result = eventFilteringPlugin.intercept(filteredEvent)

        assertNull(result)
    }

    @Test
    fun `given an allowed track event, when intercepted, then event passes through unchanged`() = runTest {
        val allowedEvent = TrackEvent(event = "Product Purchased", properties = emptyJsonObject)

        val result = eventFilteringPlugin.intercept(allowedEvent)

        assertEquals(allowedEvent, result)
        assertEquals("Product Purchased", (result as TrackEvent).event)
    }

    @Test
    fun `given multiple filtered track events, when intercepted, then all events are filtered out`() = runTest {
        val filteredEvent1 = TrackEvent(event = "Application Opened", properties = emptyJsonObject)
        val filteredEvent2 = TrackEvent(event = "Application Backgrounded", properties = emptyJsonObject)

        val result1 = eventFilteringPlugin.intercept(filteredEvent1)
        val result2 = eventFilteringPlugin.intercept(filteredEvent2)

        assertNull(result1)
        assertNull(result2)
    }

    @Test
    fun `given a screen event, when intercepted, then event passes through unchanged regardless of name`() = runTest {
        // Using a ScreenEvent as a non-TrackEvent
        val screenEvent = ScreenEvent(screenName = "Application Opened", properties = emptyJsonObject)

        val result = eventFilteringPlugin.intercept(screenEvent)

        assertEquals(screenEvent, result)
    }

    @Test
    fun `given an identify event, when intercepted, then event passes through unchanged`() = runTest {
        val identifyEvent = IdentifyEvent(
            userIdentityState = UserIdentity(
                anonymousId = "testAnonymousId",
                userId = "testUserId",
                traits = emptyJsonObject
            )
        )

        val result = eventFilteringPlugin.intercept(identifyEvent)

        assertEquals(identifyEvent, result)
    }

    @Test
    fun `given a group event, when intercepted, then event passes through unchanged`() = runTest {
        val groupEvent = GroupEvent(groupId = "testGroupId", traits = emptyJsonObject)

        val result = eventFilteringPlugin.intercept(groupEvent)

        assertEquals(groupEvent, result)
    }

    @Test
    fun `when teardown is called, then events filter list is cleared`() = runTest {
        // First verify filtering works
        val filteredEvent = TrackEvent(event = "Application Opened", properties = emptyJsonObject)
        val resultBeforeTeardown = eventFilteringPlugin.intercept(filteredEvent)
        assertNull(resultBeforeTeardown)

        // Call teardown
        eventFilteringPlugin.teardown()

        // After teardown, the same event should pass through
        val resultAfterTeardown = eventFilteringPlugin.intercept(filteredEvent)
        assertEquals(filteredEvent, resultAfterTeardown)
    }
}
