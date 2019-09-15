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


package org.apache.skywalking.apm.plugin.aliyun.sdk.oss.v2;


import com.aliyun.oss.common.auth.Credentials;
import com.aliyun.oss.common.auth.CredentialsProvider;
import com.aliyun.oss.common.comm.RequestMessage;
import com.aliyun.oss.internal.OSSOperation;
import com.google.common.collect.Maps;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.StringTag;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

public class OSSClientInterceptor implements InstanceMethodsAroundInterceptor {

    private static final ILog logger = LogManager.getLogger(OSSClientInterceptor.class);

    private final static Map<String, StringTag> TAG_MAP = Maps.newHashMap();

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {
        logger.debug("OSSClientInterceptor.beforeMethod allArguments:{}", allArguments);
        if (allArguments == null || allArguments.length != 3) {
            int length = allArguments == null ? 0 : allArguments.length;
            logger.error("aliyun oss sdk only use 2.x;method send arguments length is " + length);
            return;
        }
        RequestMessage requestMessage = (RequestMessage) allArguments[0];
        final String endpoint = requestMessage.getEndpoint() == null ? "N/A" : requestMessage.getEndpoint().toString();
        final String resourcePath = requestMessage.getResourcePath() == null ? "/" : "/" + requestMessage.getResourcePath();
        final String requestMethod = requestMessage.getMethod().name();
        AbstractSpan activeSpan = ContextManager.createLocalSpan("OSS " + requestMethod + " " + resourcePath);
        activeSpan.setComponent(ComponentsDefine.JODD_HTTP);
        Field ossOperationField = OSSOperation.class.getDeclaredField("credsProvider");
        ossOperationField.setAccessible(true);
        Object ossOperationObject = ossOperationField.get(objInst);
        if (ossOperationObject instanceof CredentialsProvider) {
            CredentialsProvider credentialsProvider = (CredentialsProvider)ossOperationObject;
            Credentials credentials = credentialsProvider.getCredentials();
            activeSpan.tag(getStringTag("oss.accessKeyId"), credentials.getAccessKeyId());
        }
        activeSpan.tag(getStringTag("oss.endpoint"), endpoint);
        activeSpan.tag(getStringTag("oss.key"), resourcePath);
        activeSpan.tag(getStringTag("oss.method"), requestMessage.getMethod().name());
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Object ret) throws Throwable {
        ContextManager.stopSpan();
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Throwable t) {
        AbstractSpan activeSpan = ContextManager.activeSpan();
        activeSpan.errorOccurred();
        activeSpan.log(t);
    }

    private StringTag getStringTag(String tag) {
        StringTag stringTag = TAG_MAP.get(tag);
        if (stringTag == null) {
            stringTag = new StringTag(tag);
            TAG_MAP.put(tag, stringTag);
        }
        return stringTag;
    }
}
