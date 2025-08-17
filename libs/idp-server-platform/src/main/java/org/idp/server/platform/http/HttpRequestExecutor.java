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
import org.idp.server.platform.oauth.OAuthAuthorizationConfiguration;
import org.idp.server.platform.oauth.OAuthAuthorizationResolver;
import org.idp.server.platform.oauth.OAuthAuthorizationResolvers;

public class HttpRequestExecutor {

  HttpClient httpClient;
  OAuthAuthorizationResolvers oAuthorizationResolvers;
  JsonConverter jsonConverter;
  LoggerWrapper log = LoggerWrapper.getLogger(HttpRequestExecutor.class);

  public HttpRequestExecutor(HttpClient httpClient) {
    this.httpClient = httpClient;
    this.oAuthorizationResolvers = new OAuthAuthorizationResolvers();
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  public HttpRequestExecutor(
      HttpClient httpClient, OAuthAuthorizationResolvers oAuthAuthorizationResolvers) {
    this.httpClient = httpClient;
    this.oAuthorizationResolvers = oAuthAuthorizationResolvers;
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  public HttpRequestExecutor(
      HttpClient httpClient,
      OAuthAuthorizationResolvers oAuthorizationResolvers,
      JsonConverter jsonConverter) {
    this.httpClient = httpClient;
    this.oAuthorizationResolvers = oAuthorizationResolvers;
    this.jsonConverter = jsonConverter;
  }

  public HttpRequestResult execute(
      HttpRequestExecutionConfigInterface configuration,
      HttpRequestBaseParams httpRequestBaseParams) {

    if (configuration.httpMethod().isGet()) {
      return get(configuration, httpRequestBaseParams);
    }

    HttpRequestDynamicMapper pathMapper =
        new HttpRequestDynamicMapper(configuration.pathMappingRules(), httpRequestBaseParams);
    Map<String, String> pathParams = pathMapper.toPathParams();
    HttpRequestUrl interpolatedUrl = configuration.httpRequestUrl().interpolate(pathParams);

    HttpRequestDynamicMapper headerMapper =
        new HttpRequestDynamicMapper(configuration.headerMappingRules(), httpRequestBaseParams);
    Map<String, String> headers = headerMapper.toHeaders();

    HttpRequestDynamicMapper bodyMapper =
        new HttpRequestDynamicMapper(configuration.bodyMappingRules(), httpRequestBaseParams);

    Map<String, Object> requestBody = bodyMapper.toBody();

    switch (configuration.httpRequestAuthType()) {
      case OAUTH2 -> {
        OAuthAuthorizationConfiguration oAuthAuthorizationConfig =
            configuration.oauthAuthorization();
        OAuthAuthorizationResolver resolver =
            oAuthorizationResolvers.get(oAuthAuthorizationConfig.type());
        String accessToken = resolver.resolve(oAuthAuthorizationConfig);
        headers.put("Authorization", "Bearer " + accessToken);
      }
      case HMAC_SHA256 -> {
        HmacAuthenticationConfig hmacAuthenticationConfig = configuration.hmacAuthentication();
        HttpHmacAuthorizationHeaderCreator httpHmacAuthorizationHeaderCreator =
            new HttpHmacAuthorizationHeaderCreator(
                hmacAuthenticationConfig.apiKey(), hmacAuthenticationConfig.secret());
        String hmacAuthentication =
            httpHmacAuthorizationHeaderCreator.create(
                configuration.httpMethod().name(),
                configuration.httpRequestUrl().value(),
                requestBody,
                hmacAuthenticationConfig.signingFields(),
                hmacAuthenticationConfig.signatureFormat());

        headers.put("Authorization", hmacAuthentication);
      }
    }

    HttpRequest.Builder httpRequestBuilder =
        HttpRequest.newBuilder().uri(URI.create(interpolatedUrl.value()));

    setHeaders(httpRequestBuilder, headers);
    setParams(httpRequestBuilder, configuration.httpMethod(), requestBody);

    HttpRequest httpRequest = httpRequestBuilder.build();

    return execute(httpRequest);
  }

  private HttpRequestResult get(
      HttpRequestExecutionConfigInterface configuration,
      HttpRequestBaseParams httpRequestBaseParams) {

    HttpRequestDynamicMapper pathMapper =
        new HttpRequestDynamicMapper(configuration.pathMappingRules(), httpRequestBaseParams);
    Map<String, String> pathParams = pathMapper.toPathParams();
    HttpRequestUrl interpolatedUrl = configuration.httpRequestUrl().interpolate(pathParams);

    HttpRequestDynamicMapper headerMapper =
        new HttpRequestDynamicMapper(configuration.headerMappingRules(), httpRequestBaseParams);
    Map<String, String> headers = headerMapper.toHeaders();
    HttpRequestDynamicMapper bodyMapper =
        new HttpRequestDynamicMapper(configuration.queryMappingRules(), httpRequestBaseParams);

    Map<String, String> queryParams = bodyMapper.toQueryParams();

    if (configuration.httpRequestAuthType().isOauth2()) {
      OAuthAuthorizationConfiguration oAuthAuthorizationConfig = configuration.oauthAuthorization();
      OAuthAuthorizationResolver resolver =
          oAuthorizationResolvers.get(oAuthAuthorizationConfig.type());
      String accessToken = resolver.resolve(oAuthAuthorizationConfig);
      headers.put("Authorization", "Bearer " + accessToken);
    }

    String urlWithQueryParams = interpolatedUrl.withQueryParams(new HttpQueryParams(queryParams));

    HttpRequest.Builder httpRequestBuilder =
        HttpRequest.newBuilder().uri(URI.create(urlWithQueryParams));

    setHeaders(httpRequestBuilder, headers);
    setParams(httpRequestBuilder, HttpMethod.GET, Map.of());

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

    return JsonNodeWrapper.fromString(httpResponse.body());
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
