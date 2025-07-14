/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.platform.http;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.log.LoggerWrapper;

public class HttpRequestExecutor {

  HttpClient httpClient;
  JsonConverter jsonConverter;
  LoggerWrapper log = LoggerWrapper.getLogger(HttpRequestExecutor.class);

  public HttpRequestExecutor(HttpClient httpClient) {
    this.httpClient = httpClient;
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  public HttpRequestResult executeWithDynamicMapping(
      HttpRequestUrl httpRequestUrl,
      HttpMethod httpMethod,
      HttpRequestBaseParams httpRequestBaseParams,
      HttpRequestStaticHeaders httpRequestStaticHeaders,
      HttpRequestStaticBody httpRequestStaticBody,
      HttpRequestMappingRules pathMappingRules,
      HttpRequestMappingRules headerMappingRules,
      HttpRequestMappingRules bodyMappingRules) {

    HttpRequestDynamicMapper pathMapper =
        new HttpRequestDynamicMapper(pathMappingRules, httpRequestBaseParams);
    Map<String, String> pathParams = pathMapper.toPathParams();
    HttpRequestUrl interpolatedUrl = httpRequestUrl.interpolate(pathParams);

    Map<String, String> headers = new HashMap<>();
    HttpRequestDynamicMapper headerMapper =
        new HttpRequestDynamicMapper(headerMappingRules, httpRequestBaseParams);
    Map<String, String> resolvedMappingHeaders = headerMapper.toHeaders();
    headers.putAll(resolvedMappingHeaders);
    headers.putAll(httpRequestStaticHeaders.toMap());

    Map<String, Object> requestBody = new HashMap<>(httpRequestStaticBody.toMap());

    HttpRequestDynamicMapper bodyMapper =
        new HttpRequestDynamicMapper(bodyMappingRules, httpRequestBaseParams);

    Map<String, Object> resolvedMappingBody = bodyMapper.toBody();
    requestBody.putAll(resolvedMappingBody);

    HttpRequest.Builder httpRequestBuilder =
        HttpRequest.newBuilder().uri(URI.create(interpolatedUrl.value()));

    setHeaders(httpRequestBuilder, headers);
    setParams(httpRequestBuilder, httpMethod, requestBody);

    HttpRequest httpRequest = httpRequestBuilder.build();

    return execute(httpRequest);
  }

  public HttpRequestResult getWithDynamicQueryMapping(
      HttpRequestUrl httpRequestUrl,
      HttpRequestBaseParams httpRequestBaseParams,
      HttpRequestStaticHeaders httpRequestStaticHeaders,
      HttpRequestMappingRules pathMappingRules,
      HttpRequestMappingRules queryMappingRules) {

    HttpRequestDynamicMapper pathMapper =
        new HttpRequestDynamicMapper(pathMappingRules, httpRequestBaseParams);
    Map<String, String> pathParams = pathMapper.toPathParams();
    HttpRequestUrl interpolatedUrl = httpRequestUrl.interpolate(pathParams);

    Map<String, String> headers = httpRequestStaticHeaders.toMap();
    HttpRequestDynamicMapper bodyMapper =
        new HttpRequestDynamicMapper(queryMappingRules, httpRequestBaseParams);

    Map<String, String> queryParams = bodyMapper.toQueryParams();

    String urlWithQueryParams = interpolatedUrl.withQueryParams(new HttpQueryParams(queryParams));

    HttpRequest.Builder httpRequestBuilder =
        HttpRequest.newBuilder().uri(URI.create(urlWithQueryParams));

    setHeaders(httpRequestBuilder, headers);
    setParams(httpRequestBuilder, HttpMethod.GET, Map.of());

    HttpRequest httpRequest = httpRequestBuilder.build();

    return execute(httpRequest);
  }

  // TODO to be more correctly
  public HttpRequestResult execute(
      HttpRequestUrl httpRequestUrl,
      HttpMethod httpMethod,
      HmacAuthenticationConfiguration hmacAuthenticationConfig,
      HttpRequestBaseParams httpRequestBaseParams,
      HttpRequestStaticHeaders httpRequestStaticHeaders,
      HttpRequestStaticBody httpRequestStaticBody,
      HttpRequestMappingRules pathMappingRules,
      HttpRequestMappingRules headerMappingRules,
      HttpRequestMappingRules bodyMappingRules) {

    HttpRequestDynamicMapper pathMapper =
        new HttpRequestDynamicMapper(pathMappingRules, httpRequestBaseParams);
    Map<String, String> pathParams = pathMapper.toPathParams();
    HttpRequestUrl interpolatedUrl = httpRequestUrl.interpolate(pathParams);

    Map<String, String> headers = httpRequestStaticHeaders.toMap();
    HttpRequestDynamicMapper headerMapper =
        new HttpRequestDynamicMapper(headerMappingRules, httpRequestBaseParams);
    Map<String, String> resolvedMappingHeaders = headerMapper.toHeaders();
    headers.putAll(resolvedMappingHeaders);
    headers.putAll(httpRequestStaticHeaders.toMap());

    Map<String, Object> requestBody = new HashMap<>();

    HttpRequestDynamicMapper bodyMapper =
        new HttpRequestDynamicMapper(bodyMappingRules, httpRequestBaseParams);
    Map<String, Object> resolvedMappingRequestBody = bodyMapper.toBody();
    requestBody.putAll(resolvedMappingRequestBody);
    requestBody.putAll(httpRequestStaticBody.toMap());

    HttpHmacAuthorizationHeaderCreator httpHmacAuthorizationHeaderCreator =
        new HttpHmacAuthorizationHeaderCreator(
            hmacAuthenticationConfig.apiKey(), hmacAuthenticationConfig.secret());
    String hmacAuthentication =
        httpHmacAuthorizationHeaderCreator.create(
            httpMethod.name(),
            httpRequestUrl.value(),
            requestBody,
            hmacAuthenticationConfig.signingFields(),
            hmacAuthenticationConfig.signatureFormat());

    headers.put("Authorization", hmacAuthentication);

    HttpRequest.Builder httpRequestBuilder =
        HttpRequest.newBuilder().uri(URI.create(interpolatedUrl.value()));

    setHeaders(httpRequestBuilder, headers);
    setParams(httpRequestBuilder, httpMethod, requestBody);

    HttpRequest httpRequest = httpRequestBuilder.build();

    return execute(httpRequest);
  }

  public HttpRequestResult execute(HttpRequest httpRequest) {
    try {

      log.info("Http Request: {} {}", httpRequest.uri(), httpRequest.method());

      HttpResponse<String> httpResponse =
          httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

      log.debug("Http Response status: {}", httpResponse.statusCode());
      log.debug("Http Response body: {}", httpResponse.body());

      JsonNodeWrapper jsonResponse = resolveResponseBody(httpResponse);

      return new HttpRequestResult(
          httpResponse.statusCode(), httpResponse.headers().map(), jsonResponse);
    } catch (IOException | InterruptedException e) {

      log.warn("Http request was error: {}", e.getMessage(), e);

      Map<String, Object> message = new HashMap<>();
      message.put("error", "client_error");
      message.put("error_description", e.getMessage());
      return new HttpRequestResult(499, Map.of(), JsonNodeWrapper.fromObject(message));
    }
  }

  private JsonNodeWrapper resolveResponseBody(HttpResponse<String> httpResponse) {
    if (httpResponse.body() == null || httpResponse.body().isEmpty()) {
      return JsonNodeWrapper.empty();
    }

    return jsonConverter.readTree(httpResponse.body());
  }

  private void setHeaders(
      HttpRequest.Builder httpRequestBuilder, Map<String, String> httpRequestStaticHeaders) {

    log.debug("Http Request headers: {}", httpRequestStaticHeaders);

    httpRequestStaticHeaders.forEach(httpRequestBuilder::header);
    if (!httpRequestStaticHeaders.containsKey("Content-Type")) {
      httpRequestBuilder.setHeader("Content-Type", "application/json");
    }
  }

  private void setParams(
      HttpRequest.Builder builder, HttpMethod httpMethod, Map<String, Object> requestBody) {

    switch (httpMethod) {
      case GET:
        builder.GET();
        break;
      case POST:
        {
          log.debug("Http Request body: {}", requestBody);
          builder.POST(HttpRequest.BodyPublishers.ofString(jsonConverter.write(requestBody)));
          break;
        }
      case PUT:
        {
          log.debug("Http Request body: {}", requestBody);
          builder.PUT(HttpRequest.BodyPublishers.ofString(jsonConverter.write(requestBody)));
          break;
        }
      case DELETE:
        {
          builder.DELETE();
          break;
        }
    }
  }
}
