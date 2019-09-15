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


import com.aliyun.oss.OSSClient;
import com.aliyun.oss.common.auth.CredentialsProvider;
import com.aliyun.oss.common.utils.HttpUtil;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import org.apache.commons.collections.MapUtils;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.StringTag;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.dependencies.com.google.common.collect.Maps;

import java.lang.reflect.Method;
import java.util.Map;

import static com.aliyun.oss.internal.OSSConstants.DEFAULT_CHARSET_NAME;

public class GeneratePresignedUrlInterceptor implements InstanceMethodsAroundInterceptor {

    private static final ILog logger = LogManager.getLogger(GeneratePresignedUrlInterceptor.class);

    private final static Map<String, StringTag> TAG_MAP = Maps.newHashMap();

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {
        logger.debug("GeneratePresignedUrlInterceptor.beforeMethod allArguments:{}", allArguments);
        if (allArguments == null || allArguments.length != 1) {
            int length = allArguments == null ? 0 : allArguments.length;
            logger.error("aliyun oss sdk only use 2.x;method send arguments length is " + length);
            return;
        }
        GeneratePresignedUrlRequest urlRequest = (GeneratePresignedUrlRequest) allArguments[0];
        AbstractSpan activeSpan = ContextManager.createLocalSpan("OSS GeneratePresignedUrl " + "/" + urlRequest.getKey());
        activeSpan.tag(getStringTag("oss.action"), "GeneratePresignedUrl");
        if (objInst instanceof OSSClient) {
            OSSClient ossClient = (OSSClient) objInst;
            CredentialsProvider credentialsProvider = ossClient.getCredentialsProvider();
            activeSpan.tag(getStringTag("oss.accessKeyId"), credentialsProvider.getCredentials().getAccessKeyId());
            activeSpan.tag(getStringTag("oss.endpoint"), ossClient.getEndpoint().toString());
        }
        activeSpan.tag(getStringTag("oss.bucketName"), urlRequest.getBucketName());
        activeSpan.tag(getStringTag("oss.key"), urlRequest.getKey());
        if (MapUtils.isNotEmpty(urlRequest.getQueryParameter())) {
            activeSpan.tag(getStringTag("oss.parameter"), HttpUtil.paramToQueryString(urlRequest.getQueryParameter(), DEFAULT_CHARSET_NAME));
        }
        if (urlRequest.getExpiration() != null) {
            activeSpan.tag(getStringTag("oss.expiration"), urlRequest.getExpiration().toString());
        }
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
