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

package org.idp.server.platform.oauth;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.idp.server.platform.json.JsonReadable;

public class OAuthAuthorizationConfiguration implements JsonReadable {

  String type;
  String tokenEndpoint;
  String clientAuthenticationType;
  String clientId;
  String clientSecret;
  String scope;
  String username;
  String password;
  String refreshToken;
  Integer cacheBufferSeconds;
  Integer cacheTtlSeconds;
  Boolean cacheEnabled;

  public OAuthAuthorizationConfiguration() {}

  public OAuthAuthorizationConfiguration(
      String type,
      String tokenEndpoint,
      String clientAuthenticationType,
      String clientId,
      String clientSecret,
      String scope,
      String username,
      String password,
      String refreshToken) {
    this(
        type,
        tokenEndpoint,
        clientAuthenticationType,
        clientId,
        clientSecret,
        scope,
        username,
        password,
        refreshToken,
        null,
        null,
        null);
  }

  public OAuthAuthorizationConfiguration(
      String type,
      String tokenEndpoint,
      String clientAuthenticationType,
      String clientId,
      String clientSecret,
      String scope,
      String username,
      String password,
      String refreshToken,
      Integer cacheBufferSeconds,
      Integer cacheTtlSeconds,
      Boolean cacheEnabled) {
    this.type = type;
    this.tokenEndpoint = tokenEndpoint;
    this.clientAuthenticationType = clientAuthenticationType;
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.scope = scope;
    this.username = username;
    this.password = password;
    this.refreshToken = refreshToken;
    this.cacheBufferSeconds = cacheBufferSeconds;
    this.cacheTtlSeconds = cacheTtlSeconds;
    this.cacheEnabled = cacheEnabled;
  }

  public String type() {
    return type;
  }

  public String tokenEndpoint() {
    return tokenEndpoint;
  }

  public String clientAuthenticationType() {
    return clientAuthenticationType;
  }

  public String clientId() {
    return clientId;
  }

  public String clientSecret() {
    return clientSecret;
  }

  public String scope() {
    return scope;
  }

  public String username() {
    return username;
  }

  public String password() {
    return password;
  }

  public String refreshToken() {
    return refreshToken;
  }

  public Integer cacheBufferSeconds() {
    return cacheBufferSeconds;
  }

  public Integer cacheTtlSeconds() {
    return cacheTtlSeconds;
  }

  public Boolean cacheEnabled() {
    return cacheEnabled;
  }

  public boolean isCacheEnabled() {
    return cacheEnabled != null && cacheEnabled;
  }

  public int getCacheBufferSeconds(int defaultValue) {
    return cacheBufferSeconds != null ? cacheBufferSeconds : defaultValue;
  }

  public int getCacheTtlSeconds(int defaultValue) {
    return cacheTtlSeconds != null ? cacheTtlSeconds : defaultValue;
  }

  public boolean exists() {
    return type != null && !type.isEmpty();
  }

  public boolean isClientSecretBasic() {
    return clientAuthenticationType != null
        && clientAuthenticationType.equals("client_secret_basic");
  }

  public boolean isClientSecretPost() {
    return clientAuthenticationType != null
        && clientAuthenticationType.equals("client_secret_post");
  }

  public boolean isClientCredentials() {
    return "client_credentials".equals(type);
  }

  public boolean isResourceOwnerPassword() {
    return "password".equals(type);
  }

  public String basicAuthenticationValue() {
    String auth = clientId + ":" + clientSecret;
    String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
    return "Basic " + encodedAuth;
  }

  public Map<String, String> toRequestValues() {
    Map<String, String> map = new HashMap<>();

    map.put("client_id", clientId);

    if (isResourceOwnerPassword()) {
      map.put("username", username);
      map.put("password", password);
    }

    if (isClientSecretPost()) {
      map.put("client_secret", clientSecret);
    }

    map.put("scope", scope);
    map.put("grant_type", type);
    return map;
  }

  public Map<String, Object> toMap() {
    HashMap<String, Object> result = new HashMap<>();
    result.put("type", type);
    result.put("token_endpoint", tokenEndpoint);
    result.put("client_authentication_type", clientAuthenticationType);
    result.put("client_id", clientId);
    result.put("client_secret", clientSecret);
    result.put("scope", scope);
    result.put("username", username);
    result.put("password", password);
    result.put("refresh_token", refreshToken);
    result.put("cache_buffer_seconds", cacheBufferSeconds);
    result.put("cache_ttl_seconds", cacheTtlSeconds);
    result.put("cache_enabled", cacheEnabled);
    return result;
  }
}
