/*
 * Copyright 2010-2015 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *    http://aws.amazon.com/apache2.0
 *
 * This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES
 * OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and
 * limitations under the License.
 */

package com.amazonaws.mobileconnectors.amazonmobileanalytics.internal.event;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amazonaws.mobileconnectors.amazonmobileanalytics.AnalyticsEvent;
import com.amazonaws.mobileconnectors.amazonmobileanalytics.MobileAnalyticsTestBase;
import com.amazonaws.mobileconnectors.amazonmobileanalytics.internal.core.AnalyticsContext;
import com.amazonaws.mobileconnectors.amazonmobileanalytics.internal.core.configuration.Configuration;
import com.amazonaws.mobileconnectors.amazonmobileanalytics.internal.delivery.DeliveryClient;
import com.amazonaws.mobileconnectors.amazonmobileanalytics.utils.AnalyticsContextBuilder;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class DefaultEventClientTest extends MobileAnalyticsTestBase {

    private static final String SDK_NAME = "AppIntelligenceSDK-Analytics";
    private static final String SDK_VERSION = "test";
    private static final String UNIQUE_ID = "abc123";
    private static final String EVENT_TYPE = "my_event";
    private static final Long TIME_STAMP = 123l;

    private DefaultEventClient target;

    @Mock
    DeliveryClient mockDeliveryClient;
    @Mock
    Configuration mockConfiguration;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        when(mockConfiguration.optString("versionKey", "ver")).thenReturn("ver");
        when(mockConfiguration.optBoolean("isAnalyticsEnabled", true)).thenReturn(true);

        AnalyticsContext mockContext = new AnalyticsContextBuilder()
                .withSdkInfo(SDK_NAME, SDK_VERSION)
                .withUniqueIdValue(UNIQUE_ID)
                .withConfiguration(mockConfiguration)
                .withDeliveryClient(mockDeliveryClient)
                .build();

        target = new DefaultEventClient(mockContext, true);

    }

    @After
    public void cleanup() {
        target = null;
    }

    @Test
    public void construct_deliveryClientIsObserver() {
        assertThat(target.getEventObservers().size(), is(1));
        assertThat(target.getEventObservers().get(0),
                sameInstance((EventObserver) mockDeliveryClient));
    }

    @Test
    public void recordEvent_globalAttributeAndMetricNone_onlyEventSpecific() {

        final InternalEvent event = MockInternalEvent.newInstance(EVENT_TYPE, TIME_STAMP);
        event.withAttribute("attr", "attr1").withMetric("metric", 1.0);

        target.recordEvent(event);

        ArgumentCaptor<InternalEvent> eventCaptor = ArgumentCaptor.forClass(InternalEvent.class);
        verify(mockDeliveryClient, times(1)).notify(eventCaptor.capture());

        final InternalEvent recievedEvent = eventCaptor.getAllValues().get(0);
        assertThat(recievedEvent.getEventType(), is(EVENT_TYPE));
        assertThat(recievedEvent.getSdkName(), is(SDK_NAME));
        assertThat(recievedEvent.getSdkVersion(), is(SDK_VERSION));
        assertThat(recievedEvent.getUniqueId().getValue(), is(UNIQUE_ID));
        assertTrue(recievedEvent.getEventTimestamp() > 0);

        assertThat(recievedEvent.getAllAttributes().size(), is(1));
        assertThat(recievedEvent.getAllMetrics().size(), is(1));
        assertThat(recievedEvent.getMetric("metric"), is(1.0));
    }

    @Test
    public void recordEvent_globalAttributeAndMetricsNotAddedAfterEventCreation() {

        final AnalyticsEvent event = target.createEvent(EVENT_TYPE);
        event.withAttribute("attr", "attr1").withMetric("metric", 1.0);

        target.addGlobalAttribute("globalAttr", "global1");
        target.addGlobalMetric("globalMetric", 100.0);
        target.recordEvent(event);

        ArgumentCaptor<InternalEvent> eventCaptor = ArgumentCaptor.forClass(InternalEvent.class);
        verify(mockDeliveryClient, times(1)).notify(eventCaptor.capture());

        final InternalEvent recievedEvent = eventCaptor.getAllValues().get(0);
        assertThat(recievedEvent.getEventType(), is(EVENT_TYPE));
        assertThat(recievedEvent.getSdkName(), is(SDK_NAME));
        assertThat(recievedEvent.getSdkVersion(), is(SDK_VERSION));
        assertThat(recievedEvent.getUniqueId().getValue(), is(UNIQUE_ID));
        assertTrue(recievedEvent.getEventTimestamp() > 0);
        assertThat(recievedEvent.getAllAttributes().size(), is(1));
        assertThat(recievedEvent.getAttribute("attr"), is("attr1"));
        assertThat(recievedEvent.getAllMetrics().size(), is(1));
        assertThat(recievedEvent.getMetric("metric").intValue(), is(1));
    }

    @Test
    public void toString_Test() throws JSONException {

        target.addGlobalAttribute("attr1", "1");
        target.addGlobalAttribute("attr2", "2");
        target.addGlobalAttribute("event_type_1", "attr3", "3");
        target.addGlobalAttribute("event_type_2", "attr4", "4");

        target.addGlobalMetric("metric1", 1.0);
        target.addGlobalMetric("metric2", 2.0);
        target.addGlobalMetric("event_type_1", "metric3", 3.0);
        target.addGlobalMetric("event_type_2", "metric4", 4.0);

        JSONObject obj = new JSONObject(target.toString());

        String className = obj.getString("class");
        assertNotNull(className);
        assertEquals(DefaultEventClient.class.getName(), className);

        JSONArray observers = obj.optJSONArray("observers");
        assertNotNull(observers);
        assertEquals(1, observers.length());

        String uniqueId = obj.optString("uniqueId", null);
        assertNotNull(uniqueId);

        JSONArray globalAttributes = obj.optJSONArray("globalAttributes");
        assertNotNull(globalAttributes);
        assertEquals(2, globalAttributes.length());
        assertTrue(globalAttributes.getJSONObject(0).has("attr1")
                || globalAttributes.getJSONObject(0).has("attr2"));
        if (globalAttributes.getJSONObject(0).has("attr1")) {
            assertEquals("1", globalAttributes.getJSONObject(0).optString("attr1", null));
        }
        if (globalAttributes.getJSONObject(0).has("attr2")) {
            assertEquals("2", globalAttributes.getJSONObject(0).optString("attr2", null));
        }

        assertTrue(globalAttributes.getJSONObject(1).has("attr1")
                || globalAttributes.getJSONObject(1).has("attr2"));
        if (globalAttributes.getJSONObject(1).has("attr1")) {
            assertEquals("1", globalAttributes.getJSONObject(1).optString("attr1", null));
        }
        if (globalAttributes.getJSONObject(1).has("attr2")) {
            assertEquals("2", globalAttributes.getJSONObject(1).optString("attr2", null));
        }

        JSONArray globalMetrics = obj.optJSONArray("globalMetrics");
        assertNotNull(globalMetrics);
        assertEquals(2, globalMetrics.length());
        assertTrue(globalMetrics.getJSONObject(0).has("metric1")
                || globalMetrics.getJSONObject(0).has("metric2"));
        if (globalMetrics.getJSONObject(0).has("metric1")) {
            assertEquals(1, globalMetrics.getJSONObject(0).optInt("metric1", 0));
        }
        if (globalMetrics.getJSONObject(0).has("metric2")) {
            assertEquals(2, globalMetrics.getJSONObject(0).optInt("metric2", 0));
        }

        assertTrue(globalMetrics.getJSONObject(1).has("metric1")
                || globalMetrics.getJSONObject(1).has("metric2"));
        if (globalMetrics.getJSONObject(1).has("metric1")) {
            assertEquals(1, globalMetrics.getJSONObject(1).optInt("metric1", 0));
        }
        if (globalMetrics.getJSONObject(1).has("metric2")) {
            assertEquals(2, globalMetrics.getJSONObject(1).optInt("metric2", 0));
        }

        JSONObject eventTypeAttributes = obj.optJSONObject("eventTypeAttributes");
        assertNotNull(eventTypeAttributes);
        assertEquals(2, eventTypeAttributes.length());
        assertTrue(eventTypeAttributes.has("event_type_1"));
        JSONArray eventType1Object = eventTypeAttributes.optJSONArray("event_type_1");
        assertEquals(1, eventType1Object.length());
        assertTrue(eventType1Object.getJSONObject(0).has("attr3"));
        assertEquals("3", eventType1Object.getJSONObject(0).optString("attr3", null));

        assertTrue(eventTypeAttributes.has("event_type_2"));
        JSONArray eventType2Object = eventTypeAttributes.optJSONArray("event_type_2");
        assertEquals(1, eventType2Object.length());
        assertTrue(eventType2Object.getJSONObject(0).has("attr4"));
        assertEquals("4", eventType2Object.getJSONObject(0).optString("attr4", null));

        JSONObject eventTypeMetrics = obj.optJSONObject("eventTypeMetrics");
        assertNotNull(eventTypeMetrics);
        assertEquals(2, eventTypeMetrics.length());
        assertTrue(eventTypeMetrics.has("event_type_1"));
        JSONArray eventType1MetricObject = eventTypeMetrics.optJSONArray("event_type_1");
        assertEquals(1, eventType1MetricObject.length());
        assertTrue(eventType1MetricObject.getJSONObject(0).has("metric3"));
        assertEquals(3, eventType1MetricObject.getJSONObject(0).optInt("metric3", 0));

        assertTrue(eventTypeMetrics.has("event_type_2"));
        JSONArray eventType2MetricObject = eventTypeMetrics.optJSONArray("event_type_2");
        assertEquals(1, eventType2MetricObject.length());
        assertTrue(eventType2MetricObject.getJSONObject(0).has("metric4"));
        assertEquals(4, eventType2MetricObject.getJSONObject(0).optInt("metric4", 0));
    }

    @Test
    public void recordEvent_nullEvent_noObserverNotified() {
        final InternalEvent event = null;
        target.recordEvent(event);
        verify(mockDeliveryClient, times(0)).notify(any(DefaultEvent.class));
    }

    @Test
    public void recordEvent_observersNotified() {
        final AnalyticsEvent event = MockInternalEvent.newInstance(EVENT_TYPE, TIME_STAMP)
                .withAttribute("attr", "attr1")
                .withMetric("metric", 1.0);

        final EventObserver firstObserver = mock(EventObserver.class);
        final EventObserver secondObserver = mock(EventObserver.class);
        target.addEventObserver(firstObserver);
        target.addEventObserver(secondObserver);
        target.recordEvent(event);

        target.removeEventObserver(secondObserver);
        target.recordEvent(event);

        verify(mockDeliveryClient, times(2)).notify(any(InternalEvent.class));
        verify(firstObserver, times(2)).notify(any(InternalEvent.class));
        verify(secondObserver, times(1)).notify(any(InternalEvent.class));
    }

    // withAttribute("attr", "attr1").

    @Test
    public void recordEvent_globalAttributesSpecificWithDiffEventTypes_willNotInterfere() {
        target.addGlobalAttribute("differentEventType", "c", "val0");
        target.addGlobalAttribute(EVENT_TYPE, "a", "val1");
        target.addGlobalAttribute(EVENT_TYPE, "b", "val2");
        target.addGlobalAttribute(EVENT_TYPE, "c", "val3");
        target.addGlobalAttribute("globalAttr", "global1");

        final AnalyticsEvent event = target.createEvent(EVENT_TYPE)
                .withAttribute("attr", "attr1")
                .withMetric("metric", 1.0);

        target.recordEvent(event);

        final AnalyticsEvent differentEvent = target.createEvent("differentEventType")
                .withAttribute("diff_attr", "diff_attr_1")
                .withMetric("diff_metric", 50.0);

        target.recordEvent(differentEvent);

        ArgumentCaptor<InternalEvent> eventCaptor = ArgumentCaptor.forClass(InternalEvent.class);
        verify(mockDeliveryClient, times(2)).notify(eventCaptor.capture());

        final InternalEvent recievedEvent = eventCaptor.getAllValues().get(0);

        assertThat(recievedEvent.getEventType(), is(EVENT_TYPE));
        assertThat(recievedEvent.getSdkName(), is(SDK_NAME));
        assertThat(recievedEvent.getSdkVersion(), is(SDK_VERSION));
        assertThat(recievedEvent.getUniqueId().getValue(), is(UNIQUE_ID));
        assertTrue(recievedEvent.getEventTimestamp() > 0);
        assertThat(recievedEvent.getAllAttributes().size(), is(5));
        assertThat(recievedEvent.getAttribute("attr"), is("attr1"));
        assertThat(recievedEvent.getAttribute("a"), is("val1"));
        assertThat(recievedEvent.getAttribute("b"), is("val2"));
        assertThat(recievedEvent.getAttribute("c"), is("val3"));
        assertThat(recievedEvent.getAttribute("globalAttr"), is("global1"));
        assertThat(recievedEvent.getAllMetrics().size(), is(1));
        assertThat(recievedEvent.getMetric("metric"), is(1.0));

        final InternalEvent differentReceivedEvent = eventCaptor.getAllValues().get(1);
        assertThat(differentReceivedEvent.getEventType(), is("differentEventType"));
        assertThat(differentReceivedEvent.getSdkName(), is(SDK_NAME));
        assertThat(differentReceivedEvent.getSdkVersion(), is(SDK_VERSION));
        assertThat(differentReceivedEvent.getUniqueId().getValue(), is(UNIQUE_ID));
        assertTrue(differentReceivedEvent.getEventTimestamp() > 0);
        assertThat(differentReceivedEvent.getAllAttributes().size(), is(3));
        assertThat(differentReceivedEvent.getAttribute("diff_attr"), is("diff_attr_1"));
        assertThat(differentReceivedEvent.getAttribute("c"), is("val0"));
        assertThat(differentReceivedEvent.getAttribute("globalAttr"), is("global1"));
        assertThat(differentReceivedEvent.getAllMetrics().size(), is(1));
        assertThat(differentReceivedEvent.getMetric("diff_metric"), is(50.0));

    }

    @Test
    public void recordEvent_globalAttributeAndMetricSpecific_doesNotOverrideLocalAttribute() {

        target.addGlobalAttribute(EVENT_TYPE, "c", "val3");
        target.addGlobalMetric(EVENT_TYPE, "metric", 3.0);

        final AnalyticsEvent event = MockInternalEvent.newInstance(EVENT_TYPE, TIME_STAMP)
                .withAttribute("c", "val4")
                .withMetric("metric", 1.0);
        target.recordEvent(event);

        ArgumentCaptor<InternalEvent> eventCaptor = ArgumentCaptor.forClass(InternalEvent.class);
        verify(mockDeliveryClient, times(1)).notify(eventCaptor.capture());

        final InternalEvent recievedEvent = eventCaptor.getAllValues().get(0);
        assertThat(recievedEvent.getAttribute("c"), is("val4"));
        assertThat(recievedEvent.getMetric("metric"), is(1.0));
    }

    @Test
    public void recordEvent_globalAttributeAndMetricGeneric_doesNotOverrideLocalAttribute() {

        target.addGlobalAttribute("c", "val2");
        target.addGlobalMetric("metric", 3.0);

        final AnalyticsEvent event = MockInternalEvent.newInstance(EVENT_TYPE, TIME_STAMP)
                .withAttribute("c", "val4")
                .withMetric("metric", 1.0);
        target.recordEvent(event);

        ArgumentCaptor<InternalEvent> eventCaptor = ArgumentCaptor.forClass(InternalEvent.class);
        verify(mockDeliveryClient, times(1)).notify(eventCaptor.capture());

        final InternalEvent recievedEvent = eventCaptor.getAllValues().get(0);
        assertThat(recievedEvent.getAttribute("c"), is("val4"));
        assertThat(recievedEvent.getMetric("metric").intValue(), is(1));
    }

    @Test
    public void recordEvent_globalMetricsSpecificWithDiffEventTypes_willNotInterfere() {
        target.addGlobalMetric("differentEventType", "c", 0.0);
        target.addGlobalMetric("differentEventType", "a", 0.0);
        target.addGlobalMetric("differentEventType", "f", 0.0);
        target.addGlobalMetric(EVENT_TYPE, "a", 1.0);
        target.addGlobalMetric(EVENT_TYPE, "b", 2.0);
        target.addGlobalMetric(EVENT_TYPE, "c", 3.0);
        target.addGlobalMetric("d", 4.0);
        target.addGlobalMetric("e", 5.0);

        final AnalyticsEvent event = target.createEvent(EVENT_TYPE);
        event.addMetric("e", 6.0);

        target.recordEvent(event);

        final AnalyticsEvent differentEvent = target.createEvent("differentEventType");
        differentEvent.addMetric("e", 7.0);

        target.recordEvent(differentEvent);

        ArgumentCaptor<InternalEvent> eventCaptor = ArgumentCaptor.forClass(InternalEvent.class);
        verify(mockDeliveryClient, times(2)).notify(eventCaptor.capture());

        final InternalEvent recievedEvent = eventCaptor.getAllValues().get(0);

        assertThat(recievedEvent.getEventType(), is(EVENT_TYPE));
        assertThat(recievedEvent.getSdkName(), is(SDK_NAME));
        assertThat(recievedEvent.getSdkVersion(), is(SDK_VERSION));
        assertThat(recievedEvent.getUniqueId().getValue(), is(UNIQUE_ID));
        assertTrue(recievedEvent.getEventTimestamp() > 0);
        assertThat(recievedEvent.getAllMetrics().size(), is(5));
        assertThat(recievedEvent.getMetric("a"), is(1.0));
        assertThat(recievedEvent.getMetric("b"), is(2.0));
        assertThat(recievedEvent.getMetric("c"), is(3.0));
        assertThat(recievedEvent.getMetric("d"), is(4.0));
        assertThat(recievedEvent.getMetric("e"), is(6.0));

        final InternalEvent differentReceivedEvent = eventCaptor.getAllValues().get(1);
        assertThat(differentReceivedEvent.getEventType(), is("differentEventType"));
        assertThat(differentReceivedEvent.getSdkName(), is(SDK_NAME));
        assertThat(differentReceivedEvent.getSdkVersion(), is(SDK_VERSION));
        assertThat(differentReceivedEvent.getUniqueId().getValue(), is(UNIQUE_ID));
        assertTrue(differentReceivedEvent.getEventTimestamp() > 0);
        assertThat(differentReceivedEvent.getAllMetrics().size(), is(5));
        assertThat(differentReceivedEvent.getMetric("a").intValue(), is(0));
        assertThat(differentReceivedEvent.getMetric("c").intValue(), is(0));
        assertThat(differentReceivedEvent.getMetric("d").intValue(), is(4));
        assertThat(differentReceivedEvent.getMetric("e").intValue(), is(7));
        assertThat(differentReceivedEvent.getMetric("f").intValue(), is(0));

    }

    @Test
    public void recordEvent_observersAddedAndRemoved() {
        target.addEventObserver(null);
        assertThat(target.getEventObservers().size(), is(1));

        target.addEventObserver(mockDeliveryClient);
        assertThat(target.getEventObservers().size(), is(1));

        target.removeEventObserver(null);
        assertThat(target.getEventObservers().size(), is(1));

        target.removeEventObserver(mockDeliveryClient);
        assertThat(target.getEventObservers().size(), is(0));

        target.removeEventObserver(mock(EventObserver.class));
        assertThat(target.getEventObservers().size(), is(0));

        target.addEventObserver(mockDeliveryClient);
        assertThat(target.getEventObservers().size(), is(1));
    }

    @Test
    public void recordEvent_globalAttributeAndMetricOrdering_eventSpecificTakesPriority() {
        target.addGlobalAttribute("a", "val1");
        target.addGlobalAttribute(EVENT_TYPE, "a", "val2");
        target.addGlobalAttribute("a", "val3");
        target.addGlobalMetric("b", 1.0);
        target.addGlobalMetric(EVENT_TYPE, "b", 2.0);
        target.addGlobalMetric("b", 3.0);

        final AnalyticsEvent event = target.createEvent(EVENT_TYPE);
        target.recordEvent(event);

        ArgumentCaptor<InternalEvent> eventCaptor = ArgumentCaptor.forClass(InternalEvent.class);
        verify(mockDeliveryClient, times(1)).notify(eventCaptor.capture());

        final InternalEvent recievedEvent = eventCaptor.getAllValues().get(0);
        assertThat(recievedEvent.getAllAttributes().size(), is(1));
        assertThat(recievedEvent.getAttribute("a"), is("val2"));
        assertThat(recievedEvent.getAllMetrics().size(), is(1));
        assertThat(recievedEvent.getMetric("b"), is(2.0));

    }

    @Test
    public void recordEvent_addAndRemoveGenericGlobalAttributes() {

        target.addGlobalAttribute("attr", "val");
        target.addGlobalAttribute("attr2", "val2");
        target.addGlobalAttribute(null, "value");
        target.addGlobalAttribute("attr3", null);

        AnalyticsEvent event = target.createEvent(EVENT_TYPE)
                .withAttribute("local_attr", "attr1")
                .withMetric("metric", 1.0);

        target.recordEvent(event);

        // remove global attrs
        target.removeGlobalAttribute("attr");
        target.removeGlobalAttribute(null);

        event = target.createEvent(EVENT_TYPE)
                .withAttribute("local_attr", "attr1")
                .withMetric("metric", 1.0);
        target.recordEvent(event);

        ArgumentCaptor<InternalEvent> eventCaptor = ArgumentCaptor.forClass(InternalEvent.class);
        verify(mockDeliveryClient, times(2)).notify(eventCaptor.capture());

        final InternalEvent firstRecievedEvent = eventCaptor.getAllValues().get(0);

        assertThat(firstRecievedEvent.getAllAttributes().size(), is(3));
        assertThat(firstRecievedEvent.getAttribute("local_attr"), is("attr1"));
        assertThat(firstRecievedEvent.getAttribute("attr"), is("val"));
        assertThat(firstRecievedEvent.getAttribute("attr2"), is("val2"));
        assertThat(firstRecievedEvent.getAllMetrics().size(), is(1));
        assertThat(firstRecievedEvent.getMetric("metric"), is(1.0));

        final InternalEvent secondRecievedEvent = eventCaptor.getAllValues().get(1);

        assertThat(secondRecievedEvent.getAllAttributes().size(), is(2));
        assertThat(secondRecievedEvent.getAttribute("local_attr"), is("attr1"));
        assertThat(secondRecievedEvent.getAttribute("attr2"), is("val2"));
        assertThat(secondRecievedEvent.getAllMetrics().size(), is(1));
        assertThat(secondRecievedEvent.getMetric("metric"), is(1.0));

    }

    @Test
    public void recordEvent_addAndRemoveGenericGlobalMetrics() {
        target.addGlobalMetric("metric", 3.0);
        target.addGlobalMetric("metric2", 121.12d);
        target.addGlobalMetric(null, 323.0);
        target.addGlobalMetric("metric3", null);

        AnalyticsEvent event = target.createEvent(EVENT_TYPE)
                .withAttribute("local_attr", "attr1")
                .withMetric("local_metric", 1.0);

        target.recordEvent(event);

        // remove global metrics
        target.removeGlobalMetric("metric");
        target.removeGlobalMetric(null);

        event = target.createEvent(EVENT_TYPE)
                .withAttribute("local_attr", "attr1")
                .withMetric("local_metric", 1.0);
        target.recordEvent(event);

        ArgumentCaptor<InternalEvent> eventCaptor = ArgumentCaptor.forClass(InternalEvent.class);
        verify(mockDeliveryClient, times(2)).notify(eventCaptor.capture());

        final InternalEvent firstRecievedEvent = eventCaptor.getAllValues().get(0);

        assertThat(firstRecievedEvent.getAllAttributes().size(), is(1));
        assertThat(firstRecievedEvent.getAttribute("local_attr"), is("attr1"));
        assertThat(firstRecievedEvent.getAllMetrics().size(), is(3));
        assertThat(firstRecievedEvent.getMetric("local_metric").intValue(), is(1));
        assertThat(firstRecievedEvent.getMetric("metric").longValue(), is(3L));
        assertThat(firstRecievedEvent.getMetric("metric2").doubleValue(), is(121.12d));

        final InternalEvent secondRecievedEvent = eventCaptor.getAllValues().get(1);

        assertThat(secondRecievedEvent.getAllAttributes().size(), is(1));
        assertThat(secondRecievedEvent.getAttribute("local_attr"), is("attr1"));
        assertThat(secondRecievedEvent.getAllMetrics().size(), is(2));
        assertThat(secondRecievedEvent.getMetric("local_metric").intValue(), is(1));
        assertThat(secondRecievedEvent.getMetric("metric2").doubleValue(), is(121.12d));
    }

    @Test
    public void recordEvent_addAndRemoveSpecificGlobalAttributes() {
        target.addGlobalAttribute(EVENT_TYPE, "attr", "val");
        target.addGlobalAttribute(EVENT_TYPE, "attr2", "val2");
        // These will not be added but should not throw exceptions
        target.addGlobalAttribute(null, "attr3", "value");
        target.addGlobalAttribute(EVENT_TYPE, null, "value");
        target.addGlobalAttribute(EVENT_TYPE, "attr3", null);

        AnalyticsEvent event = target.createEvent(EVENT_TYPE)
                .withAttribute("local_attr", "attr1")
                .withMetric("local_metric", 1.0);
        target.recordEvent(event);

        // remove global attrs
        target.removeGlobalAttribute(EVENT_TYPE, "attr");
        target.removeGlobalAttribute(null, "attr");
        target.removeGlobalAttribute(EVENT_TYPE, null);

        event = target.createEvent(EVENT_TYPE)
                .withAttribute("local_attr", "attr1")
                .withMetric("local_metric", 1.0);

        target.recordEvent(event);

        ArgumentCaptor<InternalEvent> eventCaptor = ArgumentCaptor.forClass(InternalEvent.class);
        verify(mockDeliveryClient, times(2)).notify(eventCaptor.capture());

        final InternalEvent firstRecievedEvent = eventCaptor.getAllValues().get(0);

        for (String key : firstRecievedEvent.getAllAttributes().values()) {
            System.out.println("Key: " + key);
        }

        assertThat(firstRecievedEvent.getAllAttributes().size(), is(3));
        assertThat(firstRecievedEvent.getAttribute("local_attr"), is("attr1"));
        assertThat(firstRecievedEvent.getAttribute("attr"), is("val"));
        assertThat(firstRecievedEvent.getAttribute("attr2"), is("val2"));
        assertThat(firstRecievedEvent.getAllMetrics().size(), is(1));
        assertThat(firstRecievedEvent.getMetric("local_metric").intValue(), is(1));

        final InternalEvent secondRecievedEvent = eventCaptor.getAllValues().get(1);

        assertThat(secondRecievedEvent.getAllAttributes().size(), is(2));
        assertThat(secondRecievedEvent.getAttribute("local_attr"), is("attr1"));
        assertThat(secondRecievedEvent.getAttribute("attr2"), is("val2"));
        assertThat(secondRecievedEvent.getAllMetrics().size(), is(1));
        assertThat(secondRecievedEvent.getMetric("local_metric").intValue(), is(1));
    }

    @Test
    public void recordEvent_addAndRemoveSpecificGlobalMetrics() {
        target.addGlobalMetric(EVENT_TYPE, "metric", 3.0);
        target.addGlobalMetric(EVENT_TYPE, "metric2", 121.12d);
        target.addGlobalMetric(null, 32.0);
        target.addGlobalMetric("metric3", null);

        AnalyticsEvent event = target.createEvent(EVENT_TYPE)
                .withAttribute("local_attr", "attr1")
                .withMetric("local_metric", 1.0);

        target.recordEvent(event);

        // remove global metrics
        target.removeGlobalMetric(EVENT_TYPE, "metric");
        target.removeGlobalMetric(null);

        event = target.createEvent(EVENT_TYPE)
                .withAttribute("local_attr", "attr1")
                .withMetric("local_metric", 1.0);
        target.recordEvent(event);

        ArgumentCaptor<InternalEvent> eventCaptor = ArgumentCaptor.forClass(InternalEvent.class);
        verify(mockDeliveryClient, times(2)).notify(eventCaptor.capture());

        final InternalEvent firstRecievedEvent = eventCaptor.getAllValues().get(0);

        assertThat(firstRecievedEvent.getAllAttributes().size(), is(1));
        assertThat(firstRecievedEvent.getAttribute("local_attr"), is("attr1"));
        assertThat(firstRecievedEvent.getAllMetrics().size(), is(3));
        assertThat(firstRecievedEvent.getMetric("local_metric").intValue(), is(1));
        assertThat(firstRecievedEvent.getMetric("metric").longValue(), is(3L));
        assertThat(firstRecievedEvent.getMetric("metric2").doubleValue(), is(121.12d));

        final InternalEvent secondRecievedEvent = eventCaptor.getAllValues().get(1);

        assertThat(secondRecievedEvent.getAllAttributes().size(), is(1));
        assertThat(secondRecievedEvent.getAttribute("local_attr"), is("attr1"));
        assertThat(secondRecievedEvent.getAllMetrics().size(), is(2));
        assertThat(secondRecievedEvent.getMetric("local_metric").intValue(), is(1));
        assertThat(secondRecievedEvent.getMetric("metric2").doubleValue(), is(121.12d));
    }

    @Test
    public void submitEvents_attemptDeliveryCalled() {
        target.submitEvents();
        verify(mockDeliveryClient, times(1)).attemptDelivery();
    }

    @Test
    public void recordEvent_allowCollectionDisabled_noObserverNotified() {

        AnalyticsContext mockContext = new AnalyticsContextBuilder()
                .withSdkInfo(SDK_NAME, SDK_VERSION)
                .withUniqueIdValue(UNIQUE_ID)
                .withConfiguration(mockConfiguration)
                .build();

        target = new DefaultEventClient(mockContext, false);

        InternalEvent event = MockInternalEvent.newInstance(EVENT_TYPE, TIME_STAMP);
        target = spy(target);
        target.recordEvent(event);

        verify(target, never()).notifyObservers(any(InternalEvent.class));
        verify(mockDeliveryClient, never()).notify(any(InternalEvent.class));
    }

    @Test
    public void recordEvent_analyticsDisabled_noObserverNotified() {

        AnalyticsContext mockContext = new AnalyticsContextBuilder()
                .withSdkInfo(SDK_NAME, SDK_VERSION)
                .withUniqueIdValue(UNIQUE_ID)
                .withConfiguration(mockConfiguration)
                .build();

        target = new DefaultEventClient(mockContext, true);

        when(mockConfiguration.optBoolean("isAnalyticsEnabled", true)).thenReturn(false);

        InternalEvent event = MockInternalEvent.newInstance(EVENT_TYPE, TIME_STAMP);
        target = spy(target);
        target.recordEvent(event);

        verify(target, never()).notifyObservers(any(InternalEvent.class));
        verify(mockDeliveryClient, never()).notify(any(InternalEvent.class));
    }

    @Test
    public void createEvent_eventTypeTooLong_typeNameIsTruncated() {
        AnalyticsEvent e = target
                .createEvent("123456789012345678901234567890123456789012345678901234567890");
        assertThat(e.getEventType(), is("12345678901234567890123456789012345678901234567890"));
    }
}
