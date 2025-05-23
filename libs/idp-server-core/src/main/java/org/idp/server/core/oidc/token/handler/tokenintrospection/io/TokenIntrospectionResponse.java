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

package org.idp.server.core.oidc.token.handler.tokenintrospection.io;

import java.util.Map;
import org.idp.server.core.oidc.token.OAuthToken;
import org.idp.server.platform.security.event.DefaultSecurityEventType;

public class TokenIntrospectionResponse {
  TokenIntrospectionRequestStatus status;
  OAuthToken oAuthToken;
  Map<String, Object> response;

  public TokenIntrospectionResponse(
      TokenIntrospectionRequestStatus status, OAuthToken oAuthToken, Map<String, Object> contents) {
    this.status = status;
    this.oAuthToken = oAuthToken;
    this.response = contents;
  }

  public TokenIntrospectionResponse(
      TokenIntrospectionRequestStatus status, Map<String, Object> contents) {
    this.status = status;
    this.response = contents;
  }

  public TokenIntrospectionRequestStatus status() {
    return status;
  }

  public int statusCode() {
    return status.statusCode();
  }

  public OAuthToken oAuthToken() {
    return oAuthToken;
  }

  public Map<String, Object> response() {
    return response;
  }

  public boolean isActive() {
    return Boolean.TRUE.equals(response.get("active"));
  }

  public String subject() {
    return (String) response.getOrDefault("sub", "");
  }

  public boolean isExpired() {
    return status.isExpired();
  }

  public boolean isOK() {
    return status.isOK();
  }

  public boolean hasOAuthToken() {
    return oAuthToken != null && oAuthToken.exists();
  }

  public boolean isClientCredentialsGrant() {
    return oAuthToken.isClientCredentialsGrant();
  }

  public DefaultSecurityEventType securityEventType() {
    if (isExpired()) {
      return DefaultSecurityEventType.inspect_token_failure;
    }

    if (!isOK()) {
      return DefaultSecurityEventType.inspect_token_failure;
    }

    return DefaultSecurityEventType.inspect_token_success;
  }
}
