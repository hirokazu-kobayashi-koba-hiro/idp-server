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


package org.idp.server.core.oidc.token;

import java.util.Map;
import org.idp.server.basic.type.extension.CreatedAt;
import org.idp.server.basic.type.extension.CustomProperties;
import org.idp.server.basic.type.extension.ExpiredAt;
import org.idp.server.basic.type.oauth.*;

public class AccessTokenPayload {
  TokenIssuer tokenIssuer;
  Subject subject;
  RequestedClientId requestedClientId;
  Scopes scopes;
  CustomProperties customProperties;
  CreatedAt createdAt;
  ExpiredAt expiredAt;
  Map<String, Object> values;

  public AccessTokenPayload() {}

  AccessTokenPayload(
      TokenIssuer tokenIssuer,
      Subject subject,
      RequestedClientId requestedClientId,
      Scopes scopes,
      CustomProperties customProperties,
      CreatedAt createdAt,
      ExpiredAt expiredAt,
      Map<String, Object> values) {
    this.tokenIssuer = tokenIssuer;
    this.subject = subject;
    this.requestedClientId = requestedClientId;
    this.scopes = scopes;
    this.customProperties = customProperties;
    this.createdAt = createdAt;
    this.expiredAt = expiredAt;
    this.values = values;
  }

  public TokenIssuer tokenIssuer() {
    return tokenIssuer;
  }

  public Subject subject() {
    return subject;
  }

  public RequestedClientId clientId() {
    return requestedClientId;
  }

  public Scopes scopes() {
    return scopes;
  }

  public CustomProperties customProperties() {
    return customProperties;
  }

  public CreatedAt createdAt() {
    return createdAt;
  }

  public ExpiredAt expiredAt() {
    return expiredAt;
  }

  public Map<String, Object> values() {
    return values;
  }

  public boolean hasOpenidScope() {
    return scopes.contains("openid");
  }
}
