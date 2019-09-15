/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */


package org.apache.skywalking.apm.plugin.jodd.http.v5;


import jodd.http.HttpRequest;
import jodd.http.HttpResponse;
import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.test.helper.SegmentHelper;
import org.apache.skywalking.apm.agent.test.tools.*;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;

import java.util.List;

import static org.apache.skywalking.apm.agent.test.tools.SpanAssert.assertComponent;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(org.powermock.modules.junit4.PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
@PrepareForTest(HttpRequest.class)
public class JoddHttpInterceptorTest {

    private final String httpTestUrl = "https://skywalking.apache.org/team/?test=jodd_http";

    @SegmentStoragePoint
    private SegmentStorage segmentStorage;

    @Rule
    public AgentServiceRule agentServiceRule = new AgentServiceRule();

    private JoddHttpInterceptor joddHttpInterceptor;

    @Mock
    HttpRequest httpRequest;

    @Mock
    HttpResponse httpResponse;

    private Object[] allArguments;
    private Class[] argumentTypes;

    private EnhancedInstance enhancedInstance = new EnhancedInstance() {

        private Object object;

        @Override
        public Object getSkyWalkingDynamicField() {
            return object;
        }

        @Override
        public void setSkyWalkingDynamicField(Object value) {
            this.object = value;
        }
    };

    @Before
    public void setUp() throws Exception {
        ServiceManager.INSTANCE.boot();
        httpRequest = HttpRequest.get(httpTestUrl);
        allArguments = new Object[]{httpRequest};
        argumentTypes = new Class[]{HttpRequest.class};
        joddHttpInterceptor = new JoddHttpInterceptor();
    }

    @Test
    public void testOnConstruct() {
        joddHttpInterceptor.onConstruct(enhancedInstance, allArguments);
        assertThat(enhancedInstance.getSkyWalkingDynamicField(), is(allArguments[0]));
    }

    @Test
    public void testHttpRequest() throws Throwable {
        joddHttpInterceptor.onConstruct(enhancedInstance, allArguments);
        joddHttpInterceptor.beforeMethod(enhancedInstance, null, allArguments, argumentTypes, null);

        HttpResponse httpResponse = mock(HttpResponse.class);
        when(httpResponse.statusCode()).thenReturn(200);
        joddHttpInterceptor.afterMethod(enhancedInstance, null, allArguments, argumentTypes, httpResponse);

        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> activeSpan = SegmentHelper.getSpans(traceSegment);

        assertSpan(activeSpan.get(0));
        SpanAssert.assertOccurException(activeSpan.get(0), false);
    }

    @Test
    public void testStatusCodeNotEquals200() throws Throwable {

    }

    @Test
    public void testHttpClientWithException() throws Throwable {

    }

    private void assertSpan(AbstractTracingSpan span) {
        assertComponent(span, ComponentsDefine.JODD_HTTP);
        SpanAssert.assertLayer(span, SpanLayer.HTTP);
        SpanAssert.assertTag(span, 1, "GET");
        SpanAssert.assertTag(span, 0, httpTestUrl.substring(0,httpTestUrl.indexOf("?")));
        assertThat(span.isExit(), is(true));
        assertThat(span.getOperationName(), is("/team/"));
    }


}
