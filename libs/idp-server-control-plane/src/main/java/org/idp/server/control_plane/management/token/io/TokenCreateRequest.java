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

package org.idp.server.control_plane.management.token.io;

import java.util.HashMap;
import java.util.Map;

public class TokenCreateRequest implements TokenManagementRequest {

  private final Map<String, Object> body;

  public TokenCreateRequest(Map<String, Object> body) {
    this.body = body;
  }

  public String clientId() {
    return (String) body.get("client_id");
  }

  public String scopes() {
    return (String) body.get("scopes");
  }

  public boolean hasUserId() {
    return body.containsKey("user_id") && body.get("user_id") != null;
  }

  public String userId() {
    return (String) body.get("user_id");
  }

  public boolean hasAccessTokenDuration() {
    return body.containsKey("access_token_duration") && body.get("access_token_duration") != null;
  }

  public long accessTokenDuration() {
    Object value = body.get("access_token_duration");
    if (value instanceof Number) {
      return ((Number) value).longValue();
    }
    return Long.parseLong(value.toString());
  }

  public boolean hasRefreshTokenDuration() {
    return body.containsKey("refresh_token_duration") && body.get("refresh_token_duration") != null;
  }

  public long refreshTokenDuration() {
    Object value = body.get("refresh_token_duration");
    if (value instanceof Number) {
      return ((Number) value).longValue();
    }
    return Long.parseLong(value.toString());
  }

  public boolean hasTokenFormat() {
    return body.containsKey("token_format") && body.get("token_format") != null;
  }

  public String tokenFormat() {
    return (String) body.get("token_format");
  }

  public boolean isJwtFormat() {
    return "jwt".equalsIgnoreCase(tokenFormat());
  }

  public boolean isOpaqueFormat() {
    return "opaque".equalsIgnoreCase(tokenFormat());
  }

  public boolean hasCustomClaims() {
    return body.containsKey("custom_claims") && body.get("custom_claims") instanceof Map;
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> customClaims() {
    return (Map<String, Object>) body.get("custom_claims");
  }

  @Override
  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("client_id", clientId());
    map.put("scopes", scopes());
    if (hasUserId()) {
      map.put("user_id", userId());
    }
    return map;
  }
}
