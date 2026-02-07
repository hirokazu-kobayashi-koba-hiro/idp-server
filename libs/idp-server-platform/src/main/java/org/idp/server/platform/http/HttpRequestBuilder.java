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

import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.Map;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.oauth.OAuthAuthorizationConfiguration;
import org.idp.server.platform.oauth.OAuthAuthorizationResolver;
import org.idp.server.platform.oauth.OAuthAuthorizationResolvers;

/**
 * Builds HTTP requests from configuration with dynamic parameter mapping and authentication.
 *
 * <p>Supports:
 *
 * <ul>
 *   <li>Dynamic URL/header/body mapping from parameters
 *   <li>OAuth 2.0 Bearer token authentication
 *   <li>HMAC SHA-256 authentication
 *   <li>GET/POST/PUT/DELETE methods
 *   <li>application/json and application/x-www-form-urlencoded
 * </ul>
 *
 * @see HttpRequestExecutionConfigInterface
 * @see HttpRequestDynamicMapper
 */
public class HttpRequestBuilder {

  private final OAuthAuthorizationResolvers oAuthAuthorizationResolvers;
  private final JsonConverter jsonConverter;
  private final LoggerWrapper log = LoggerWrapper.getLogger(HttpRequestBuilder.class);

  public HttpRequestBuilder(OAuthAuthorizationResolvers oAuthAuthorizationResolvers) {
    this.oAuthAuthorizationResolvers = oAuthAuthorizationResolvers;
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  public HttpRequestBuilder(
      OAuthAuthorizationResolvers oAuthAuthorizationResolvers, JsonConverter jsonConverter) {
    this.oAuthAuthorizationResolvers = oAuthAuthorizationResolvers;
    this.jsonConverter = jsonConverter;
  }

  /**
   * Builds HTTP request from configuration and parameters.
   *
   * @param configuration request configuration
   * @param httpRequestBaseParams parameters to map into request
   * @return built HTTP request
   */
  public HttpRequest build(
      HttpRequestExecutionConfigInterface configuration,
      HttpRequestBaseParams httpRequestBaseParams) {

    if (configuration.httpMethod().isGet()) {
      return buildGetRequest(configuration, httpRequestBaseParams);
    }

    return buildPostPutDeleteRequest(configuration, httpRequestBaseParams);
  }

  private HttpRequest buildGetRequest(
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
          oAuthAuthorizationResolvers.get(oAuthAuthorizationConfig.type());
      String accessToken = resolver.resolve(oAuthAuthorizationConfig);
      headers.put("Authorization", "Bearer " + accessToken);
    }

    String urlWithQueryParams = interpolatedUrl.withQueryParams(new HttpQueryParams(queryParams));

    HttpRequest.Builder httpRequestBuilder =
        HttpRequest.newBuilder()
            .uri(URI.create(urlWithQueryParams))
            .timeout(Duration.ofSeconds(configuration.requestTimeoutSeconds()));

    setHeaders(httpRequestBuilder, headers, HttpMethod.GET);
    setParams(httpRequestBuilder, HttpMethod.GET, headers, Map.of());

    return httpRequestBuilder.build();
  }

  private HttpRequest buildPostPutDeleteRequest(
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
        new HttpRequestDynamicMapper(configuration.bodyMappingRules(), httpRequestBaseParams);

    Map<String, Object> requestBody = bodyMapper.toBody();

    switch (configuration.httpRequestAuthType()) {
      case OAUTH2 -> {
        OAuthAuthorizationConfiguration oAuthAuthorizationConfig =
            configuration.oauthAuthorization();
        OAuthAuthorizationResolver resolver =
            oAuthAuthorizationResolvers.get(oAuthAuthorizationConfig.type());
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
        HttpRequest.newBuilder()
            .uri(URI.create(interpolatedUrl.value()))
            .timeout(Duration.ofSeconds(configuration.requestTimeoutSeconds()));

    setHeaders(httpRequestBuilder, headers, configuration.httpMethod());
    setParams(httpRequestBuilder, configuration.httpMethod(), headers, requestBody);

    return httpRequestBuilder.build();
  }

  private void setHeaders(
      HttpRequest.Builder httpRequestBuilder,
      Map<String, String> httpRequestStaticHeaders,
      HttpMethod httpMethod) {

    httpRequestStaticHeaders.forEach(httpRequestBuilder::header);
    if (!httpMethod.isGet() && !httpRequestStaticHeaders.containsKey("Content-Type")) {
      httpRequestBuilder.setHeader("Content-Type", "application/json");
    }
  }

  private void setParams(
      HttpRequest.Builder builder,
      HttpMethod httpMethod,
      Map<String, String> headers,
      Map<String, Object> requestBody) {

    switch (httpMethod) {
      case GET:
        builder.GET();
        break;
      case POST:
        {
          log.debug("Http Request body: {}", jsonConverter.write(requestBody));
          if ("application/x-www-form-urlencoded".equals(headers.get("Content-Type"))) {
            HttpQueryParams httpQueryParams = HttpQueryParams.fromMapObject(requestBody);
            builder.POST(HttpRequest.BodyPublishers.ofString(httpQueryParams.params()));
          } else {
            builder.POST(HttpRequest.BodyPublishers.ofString(jsonConverter.write(requestBody)));
          }
          break;
        }
      case PUT:
        {
          log.debug("Http Request body: {}", jsonConverter.write(requestBody));
          if ("application/x-www-form-urlencoded".equals(headers.get("Content-Type"))) {
            HttpQueryParams httpQueryParams = HttpQueryParams.fromMapObject(requestBody);
            builder.PUT(HttpRequest.BodyPublishers.ofString(httpQueryParams.params()));
          } else {
            builder.PUT(HttpRequest.BodyPublishers.ofString(jsonConverter.write(requestBody)));
          }
          break;
        }
      case DELETE:
        {
          log.debug("Http Request body: {}", jsonConverter.write(requestBody));
          if ("application/x-www-form-urlencoded".equals(headers.get("Content-Type"))) {
            HttpQueryParams httpQueryParams = HttpQueryParams.fromMapObject(requestBody);
            builder.method("DELETE", HttpRequest.BodyPublishers.ofString(httpQueryParams.params()));
          } else {
            builder.method(
                "DELETE", HttpRequest.BodyPublishers.ofString(jsonConverter.write(requestBody)));
          }
          break;
        }
    }
  }
}
