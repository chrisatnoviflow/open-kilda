/* Copyright 2018 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.store.common.helper;

import org.openkilda.constants.HttpError;
import org.openkilda.exception.ExternalSystemException;
import org.openkilda.exception.RestCallFailedException;
import org.openkilda.integration.exception.InvalidResponseException;
import org.openkilda.model.response.ErrorMessage;
import org.openkilda.store.common.model.ApiRequestDto;
import org.openkilda.utility.IoUtil;
import org.openkilda.utility.StringUtil;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;


/**
 * The Class StoreRestClientManager.
 */
@Component
public class StoreRestClientManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(StoreRestClientManager.class);

    @Autowired
    private ObjectMapper mapper;
    
    /**
     * Invoke.
     *
     * @param apiUrl the api url
     * @param httpMethod the http method
     * @param payload the payload
     * @param contentType the content type
     * @param basicAuth the basic auth
     * @return the http response
     */
    public HttpResponse invoke(final ApiRequestDto apiRequestDto) {
        LOGGER.info("[invoke] - Start");

        HttpResponse httpResponse = null;
        
        String url = apiRequestDto.getUrl();
        String headers = apiRequestDto.getHeader();
        HttpMethod httpMethod = apiRequestDto.getHttpMethod();
        String payload = apiRequestDto.getPayload();
        
        try {
            HttpClient client = HttpClients.createDefault();
            HttpUriRequest httpUriRequest = null;
            HttpEntityEnclosingRequestBase httpEntityEnclosingRequest = null;

            // Initializing Request
            if (HttpMethod.POST.equals(httpMethod)) {
                httpEntityEnclosingRequest = new HttpPost(url);
            } else if (HttpMethod.PUT.equals(httpMethod)) {
                httpEntityEnclosingRequest = new HttpPut(url);
            } else if (HttpMethod.DELETE.equals(httpMethod)) {
                httpUriRequest = new HttpDelete(url);
            } else if (HttpMethod.PATCH.equals(httpMethod)) {
                httpUriRequest = new HttpPatch(url);
            } else {
                httpUriRequest = new HttpGet(url);
            }

            if (!HttpMethod.POST.equals(httpMethod) && !HttpMethod.PUT.equals(httpMethod)) {
                if (!StringUtil.isNullOrEmpty(headers)) {
                    Map<String, String> headersMap = getHeaders(headers);
                    for(Entry<String,String> header : headersMap.entrySet()){
                        httpUriRequest.setHeader(header.getKey(), header.getValue());                    
                    }
                }
            }

            if (HttpMethod.POST.equals(httpMethod) || HttpMethod.PUT.equals(httpMethod)) {
                LOGGER.info("[invoke] Executing POST/ PUT request : httpEntityEnclosingRequest : "
                        + httpEntityEnclosingRequest + " : payload : " + payload);
                if (!StringUtil.isNullOrEmpty(headers)) {
                    Map<String, String> headersMap = getHeaders(headers);
                    for(Entry<String,String> header : headersMap.entrySet()){
                        httpEntityEnclosingRequest.setHeader(header.getKey(), header.getValue());                    
                    }
                }
                // Setting request payload
                httpEntityEnclosingRequest.setEntity(new StringEntity(payload));
                httpResponse = client.execute(httpEntityEnclosingRequest);
                LOGGER.debug("[invoke] Call executed successfully");
            } else {
                LOGGER.info("[invoke] Executing : httpUriRequest : " + httpUriRequest);
                httpResponse = client.execute(httpUriRequest);
                LOGGER.info("[invoke] Call executed successfully");
            }

        } catch (Exception e) {
            LOGGER.error("[invoke] Exception: ", e);
            throw new RestCallFailedException(e);
        }
        LOGGER.info("[invoke] - End");
        return httpResponse;
    }

    /**
     * Gets the headers.
     *
     * @param headers the header
     * @return the headers
     */
    private Map<String,String> getHeaders(final String headers){
        Map<String,String> headersMap = new HashMap<String, String>();
        String[] headersAry = headers.split(":");
        if(headersAry.length > 1){
            StringBuilder key = null;
            StringBuilder value = null;
            for(int i = 0; i < headersAry.length; i++)
            {
                if(i == 0 || i%2 ==0){
                    key = new StringBuilder(headersAry[i]);
                }else{
                    value = new StringBuilder(headersAry[i]);
                }
                headersMap.put(key.toString(), value.toString());
            }
        }
        return headersMap;
    }
    /**
     * Gets the response list.
     *
     * @param <T> the generic type
     * @param response the response
     * @param responseClass the response class
     * @return the response list
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> getResponseList(final HttpResponse response, final Class<T> responseClass) {
        return getResponse(response, List.class, responseClass);
    }

    /**
     * Gets the response.
     *
     * @param <T> the generic type
     * @param response the response
     * @param responseClass the response class
     * @return the response
     */
    public <T> T getResponse(final HttpResponse response, final Class<T> responseClass) {
        return getResponse(response, responseClass, null);
    }

    /**
     * Gets the response.
     *
     * @param <T> the generic type
     * @param <E> the element type
     * @param response the response
     * @param responseClass the response class
     * @param dependentClass the dependent class
     * @return the response
     */
    private <T, E> T getResponse(final HttpResponse response, final Class<T> responseClass,
            final Class<E> dependentClass) {
        T obj = null;
        try {
            LOGGER.info("[getResponse]  : StatusCode " + response.getStatusLine().getStatusCode());

            if (response.getStatusLine().getStatusCode() != HttpStatus.NO_CONTENT.value()) {
                String responseEntity = IoUtil.toString(response.getEntity().getContent());

                LOGGER.debug("[getResponse]  : response object " + responseEntity);
                if (!(HttpStatus.valueOf(response.getStatusLine().getStatusCode()).is2xxSuccessful()
                        && response.getEntity() != null)) {
                    String errorMessage = null;
                    try {
                        if (responseEntity.startsWith("[")) {
                            responseEntity = responseEntity.replaceFirst("]", "").trim();
                        }
                        if (responseEntity.endsWith("]")) {
                            responseEntity = responseEntity.replace("]", "").trim();
                        }

                        errorMessage = mapper.readValue(responseEntity, ErrorMessage.class).getMessage();

                    } catch (Exception e) {
                        throw e;
                    }
                    LOGGER.error("[getResponse] Exception : " + responseEntity);
                    throw new ExternalSystemException(response.getStatusLine().getStatusCode(), errorMessage);

                } else {
                    if (dependentClass == null) {
                        obj = mapper.readValue(responseEntity, responseClass);
                    } else {
                        obj = mapper.readValue(responseEntity, TypeFactory.defaultInstance()
                                .constructCollectionLikeType(responseClass, dependentClass));
                    }
                }
            }
        } catch (IOException e) {
            throw new RestCallFailedException(e.getMessage());
        }
        return obj;
    }

    /**
     * Checks if is valid response.
     *
     * @param response the response
     * @return true, if is valid response
     */
    public static boolean isValidResponse(final HttpResponse response) {
        LOGGER.debug("[isValidResponse] Response Code " + response.getStatusLine().getStatusCode());
        boolean isValid = response.getStatusLine().getStatusCode() >= HttpStatus.OK.value()
                && response.getStatusLine().getStatusCode() < HttpStatus.MULTIPLE_CHOICES.value()
                && response.getEntity() != null;
        if (isValid) {
            return true;
        } else {
            try {
                String content = IoUtil.toString(response.getEntity().getContent());
                LOGGER.error("[getResponse] Invalid Response. Status Code: " + response.getStatusLine().getStatusCode()
                        + ", content: " + content);
                throw new InvalidResponseException(response.getStatusLine().getStatusCode(), content);
            } catch (IOException exception) {
                LOGGER.error("[getResponse] Exception :" + exception.getMessage(), exception);
                throw new InvalidResponseException(HttpError.INTERNAL_ERROR.getCode(),
                        HttpError.INTERNAL_ERROR.getMessage());
            }
        }
    }
}