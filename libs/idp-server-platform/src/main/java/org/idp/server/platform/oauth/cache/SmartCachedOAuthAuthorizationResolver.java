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

package org.idp.server.platform.oauth.cache;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import org.idp.server.platform.datasource.cache.CacheStore;
import org.idp.server.platform.http.HttpNetworkErrorException;
import org.idp.server.platform.http.HttpQueryParams;
import org.idp.server.platform.http.SsrfProtectedHttpClient;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.oauth.OAuthAuthorizationConfiguration;
import org.idp.server.platform.oauth.OAuthAuthorizationResolver;

public class SmartCachedOAuthAuthorizationResolver implements OAuthAuthorizationResolver {

  private final OAuthAuthorizationResolver delegate;
  private final CacheStore cacheStore;
  private final int bufferSeconds;
  private final int defaultTtlSeconds;
  private final SsrfProtectedHttpClient ssrfProtectedHttpClient;
  private final JsonConverter jsonConverter;
  private final LoggerWrapper log =
      LoggerWrapper.getLogger(SmartCachedOAuthAuthorizationResolver.class);

  public SmartCachedOAuthAuthorizationResolver(
      OAuthAuthorizationResolver delegate,
      CacheStore cacheStore,
      int bufferSeconds,
      int defaultTtlSeconds,
      SsrfProtectedHttpClient ssrfProtectedHttpClient) {
    this.delegate = delegate;
    this.cacheStore = cacheStore;
    this.bufferSeconds = bufferSeconds;
    this.defaultTtlSeconds = defaultTtlSeconds;
    this.ssrfProtectedHttpClient = ssrfProtectedHttpClient;
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public String type() {
    return delegate.type();
  }

  @Override
  public String resolve(OAuthAuthorizationConfiguration config) {
    // Check if caching is disabled for this specific configuration
    if (!config.isCacheEnabled()) {
      log.debug("Cache disabled for configuration, falling back to delegate");
      return delegate.resolve(config);
    }

    String cacheKey = CachedAccessToken.generateCacheKey(config);

    Optional<CachedAccessToken> cachedToken = cacheStore.find(cacheKey, CachedAccessToken.class);

    if (cachedToken.isPresent() && cachedToken.get().isValid()) {
      log.debug(
          "Using cached access token for key: {}, remaining time: {}s",
          cacheKey,
          cachedToken.get().getRemainingTimeSeconds());
      return cachedToken.get().getAccessToken();
    }

    if (cachedToken.isPresent()) {
      log.debug("Cached token expired for key: {}, fetching new token", cacheKey);
    } else {
      log.debug("Cache miss for key: {}, fetching new token", cacheKey);
    }

    try {
      OAuthTokenResponse tokenResponse = executeTokenRequest(config);

      if (!tokenResponse.hasAccessToken()) {
        log.warn("Token response does not contain access_token, falling back to delegate");
        return delegate.resolve(config);
      }

      int configTtlSeconds = config.getCacheTtlSeconds(defaultTtlSeconds);
      int configBufferSeconds = config.getCacheBufferSeconds(bufferSeconds);

      int expiresIn =
          tokenResponse.hasExpiresIn() ? tokenResponse.getExpiresIn() : configTtlSeconds;

      CachedAccessToken newCachedToken =
          new CachedAccessToken(tokenResponse.getAccessToken(), expiresIn, configBufferSeconds);
      cacheStore.put(cacheKey, newCachedToken, expiresIn);

      log.debug("Cached new access token for key: {}, expires in: {}s", cacheKey, expiresIn);

      return tokenResponse.getAccessToken();

    } catch (Exception e) {
      log.error("Failed to execute token request with caching, falling back to delegate", e);
      return delegate.resolve(config);
    }
  }

  private OAuthTokenResponse executeTokenRequest(OAuthAuthorizationConfiguration config) {
    HttpQueryParams httpQueryParams = new HttpQueryParams(config.toRequestValues());

    HttpRequest.Builder builder =
        HttpRequest.newBuilder()
            .uri(URI.create(config.tokenEndpoint()))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(httpQueryParams.params()));

    if (config.isClientSecretBasic()) {
      builder.header("Authorization", config.basicAuthenticationValue());
    }

    HttpRequest request = builder.build();

    log.debug("Executing token request to: {}", config.tokenEndpoint());

    HttpResponse<String> response = ssrfProtectedHttpClient.send(request);

    log.debug("Token response status: {}", response.statusCode());

    if (response.statusCode() >= 400) {
      throw new HttpNetworkErrorException(
          "Token endpoint returned non-200 status: " + response.statusCode(),
          new RuntimeException("HTTP status: " + response.statusCode()));
    }

    JsonNodeWrapper jsonNodeWrapper = jsonConverter.readTree(response.body());

    OAuthTokenResponse tokenResponse = new OAuthTokenResponse();
    tokenResponse.setAccessToken(jsonNodeWrapper.getValueOrEmptyAsString("access_token"));
    tokenResponse.setTokenType(jsonNodeWrapper.getValueOrEmptyAsString("token_type"));
    tokenResponse.setExpiresIn(jsonNodeWrapper.getValueAsInt("expires_in"));
    tokenResponse.setScope(jsonNodeWrapper.getValueOrEmptyAsString("scope"));
    tokenResponse.setRefreshToken(jsonNodeWrapper.getValueOrEmptyAsString("refresh_token"));

    return tokenResponse;
  }

  @Override
  public void invalidateCache(OAuthAuthorizationConfiguration config) {
    String cacheKey = CachedAccessToken.generateCacheKey(config);
    cacheStore.delete(cacheKey);
    log.info("Invalidated cached access token for key: {}", cacheKey);
  }
}
