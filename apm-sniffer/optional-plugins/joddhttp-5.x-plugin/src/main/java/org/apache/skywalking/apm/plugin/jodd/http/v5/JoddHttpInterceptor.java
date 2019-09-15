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
import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

import java.lang.reflect.Method;

public class JoddHttpInterceptor implements InstanceMethodsAroundInterceptor, InstanceConstructorInterceptor {

    private static final ILog logger = LogManager.getLogger(JoddHttpInterceptor.class);

    @Override public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {
        if (objInst == null) {
            logger.error("beforeMethod.objInst is null");
            return;
        }
        final HttpRequest httpRequest = (HttpRequest) objInst;
        final ContextCarrier contextCarrier = new ContextCarrier();
        String remotePeer = httpRequest.host() + ":" + httpRequest.port();
        AbstractSpan activeSpan = ContextManager.createExitSpan(httpRequest.path(), contextCarrier, remotePeer);
        activeSpan.setComponent(ComponentsDefine.HTTPCLIENT);
        Tags.URL.set(activeSpan, httpRequest.hostUrl() + httpRequest.path());
        Tags.HTTP.METHOD.set(activeSpan, httpRequest.method());
        SpanLayer.asHttp(activeSpan);

        CarrierItem next = contextCarrier.items();
        while (next.hasNext()) {
            next = next.next();
            httpRequest.header(next.getHeadKey(), next.getHeadValue());
        }
    }

    @Override public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Object ret) throws Throwable {
        if (ret != null) {
            HttpResponse response = (HttpResponse)ret;
            int statusCode = response.statusCode();
            AbstractSpan activeSpan = ContextManager.activeSpan();
            if (ContextManager.isActive()) {
                Tags.STATUS_CODE.set(activeSpan, Integer.toString(statusCode));
                if (statusCode >= 400) {
                    activeSpan.errorOccurred();
                }
                ContextManager.stopSpan(activeSpan);
            }
        }
        return ret;
    }

    @Override public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Throwable t) {
        AbstractSpan activeSpan = ContextManager.activeSpan();
        activeSpan.errorOccurred();
        activeSpan.log(t);
    }

    @Override public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {
        if (allArguments != null && allArguments.length > 0) {
            objInst.setSkyWalkingDynamicField(allArguments[0]);
        }
    }
}
