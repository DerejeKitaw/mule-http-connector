/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.http.api.request;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static org.mule.runtime.extension.api.annotation.param.display.Placement.DEFAULT_TAB;
import static org.slf4j.LoggerFactory.getLogger;

import org.mule.extension.http.api.HttpResponseAttributes;
import org.mule.extension.http.api.KeyValuePair;
import org.mule.extension.http.api.request.authentication.HttpRequestAuthentication;
import org.mule.extension.http.api.request.authentication.UsernamePasswordAuthentication;
import org.mule.extension.http.api.request.validator.ResponseValidator;
import org.mule.extension.http.api.request.validator.ResponseValidatorTypedException;
import org.mule.extension.http.api.request.validator.SuccessStatusCodeValidator;
import org.mule.extension.http.internal.request.HttpRequesterCookieConfig;
import org.mule.extension.http.internal.request.HttpRequesterProvider;
import org.mule.extension.http.internal.request.HttpResponseToResult;
import org.mule.extension.http.internal.request.RequestConnectionParams;
import org.mule.extension.http.internal.request.client.HttpExtensionClient;
import org.mule.runtime.api.util.MultiMap;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.annotation.param.display.Placement;
import org.mule.runtime.extension.api.runtime.operation.Result;
import org.mule.runtime.http.api.client.auth.HttpAuthentication;
import org.mule.runtime.http.api.domain.message.request.HttpRequest;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import org.slf4j.Logger;

import java.net.CookieManager;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Requester connectivity validator. It validates the connections created by the {@link HttpRequesterProvider}.
 *
 * @since 1.7
 */
public class HttpConnectivityValidator {

  private static final Logger LOGGER = getLogger(HttpConnectivityValidator.class);

  @Parameter
  @DisplayName("Request Path")
  @Placement(tab = DEFAULT_TAB, order = 1)
  private String testPath = "/";

  /**
   * HTTP Method for the request to be sent.
   */
  @Parameter
  @DisplayName("Request HTTP Method")
  @Optional(defaultValue = "GET")
  @Placement(order = 2)
  private String testMethod;

  /**
   * The body of the response message
   */
  @Parameter
  @DisplayName("Request Body")
  @Optional(defaultValue = "")
  @Placement(order = 3)
  private String testBody;

  /**
   * HTTP headers the message should include.
   */
  @Parameter
  @Optional
  @DisplayName("HTTP Headers")
  @Placement(order = 4)
  private List<KeyValuePair> testHeaders = emptyList();

  /**
   * Query parameters the request should include.
   */
  @Parameter
  @Optional
  @DisplayName("Query Parameters")
  @Placement(order = 5)
  private List<KeyValuePair> testQueryParams = emptyList();

  @Parameter
  @Optional
  @Placement(order = 6)
  private ResponseValidator responseValidator;

  private SuccessStatusCodeValidator defaultStatusCodeValidator = new SuccessStatusCodeValidator("0..399");

  public void validate(HttpExtensionClient client, RequestConnectionParams connectionParams)
      throws ExecutionException, InterruptedException, ResponseValidatorTypedException {
    HttpRequest request = buildTestRequest(connectionParams);
    Result<Object, HttpResponseAttributes> result = sendRequest(client, request);
    validateResult(request, result);
  }

  private void validateResult(HttpRequest request, Result result) {
    getResponseValidator().validate(result, request);
  }

  private Result<Object, HttpResponseAttributes> sendRequest(HttpExtensionClient client, HttpRequest request)
      throws InterruptedException, ExecutionException {
    HttpResponse response =
        client.send(request, 999999, false, resolveAuthentication(client)).get();

    Result<Object, HttpResponseAttributes> result = new HttpResponseToResult()
        .convert(new VoidHttpRequesterCookieConfig(), null, response, response.getEntity(), response.getEntity()::getContent,
                 request.getUri());
    return result;
  }

  private HttpRequest buildTestRequest(RequestConnectionParams connectionParams) {
    String uriString = getUriString(connectionParams);
    return HttpRequest.builder()
        .uri(uriString)
        .method(testMethod)
        .headers(toMultiMap(testHeaders))
        .queryParams(toMultiMap(testQueryParams))
        .build();
  }

  private static MultiMap<String, String> toMultiMap(List<? extends KeyValuePair> asList) {
    MultiMap<String, String> asMultiMap = new MultiMap<>();
    asList.forEach(pair -> asMultiMap.put(pair.getKey(), pair.getValue()));
    return asMultiMap;
  }

  private String getUriString(RequestConnectionParams connectionParams) {
    return format("%s://%s:%s%s", connectionParams.getProtocol().getScheme(), connectionParams.getHost(),
                  connectionParams.getPort(), testPath);
  }

  private ResponseValidator getResponseValidator() {
    return responseValidator == null ? defaultStatusCodeValidator : responseValidator;
  }

  private static class VoidHttpRequesterCookieConfig implements HttpRequesterCookieConfig {

    @Override
    public boolean isEnableCookies() {
      return false;
    }

    @Override
    public CookieManager getCookieManager() {
      return null;
    }
  }

  private static HttpAuthentication resolveAuthentication(HttpExtensionClient client) {
    HttpRequestAuthentication authentication = client.getDefaultAuthentication();
    if (authentication instanceof UsernamePasswordAuthentication) {
      return (HttpAuthentication) authentication;
    } else {
      return null;
    }
  }
}
