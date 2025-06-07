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

package org.idp.server.core.oidc.authentication;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.idp.server.basic.type.AuthorizationFlow;
import org.idp.server.basic.type.oauth.RequestedClientId;
import org.idp.server.basic.type.oauth.Scopes;
import org.idp.server.basic.type.oidc.AcrValues;
import org.idp.server.core.oidc.federation.FederationInteractionResult;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public class AuthenticationRequest {

  AuthorizationFlow authorizationFlow;
  TenantIdentifier tenantIdentifier;
  RequestedClientId requestedClientId;
  User user;
  AuthenticationContext context;
  LocalDateTime createdAt;
  LocalDateTime expiredAt;

  public AuthenticationRequest() {}

  public AuthenticationRequest(
      AuthorizationFlow authorizationFlow,
      TenantIdentifier tenantIdentifier,
      RequestedClientId requestedClientId,
      User user,
      AuthenticationContext context,
      LocalDateTime createdAt,
      LocalDateTime expiredAt) {
    this.authorizationFlow = authorizationFlow;
    this.tenantIdentifier = tenantIdentifier;
    this.requestedClientId = requestedClientId;
    this.user = user;
    this.context = context;
    this.createdAt = createdAt;
    this.expiredAt = expiredAt;
  }

  public AuthorizationFlow authorizationFlow() {
    return authorizationFlow;
  }

  public TenantIdentifier tenantIdentifier() {
    return tenantIdentifier;
  }

  public RequestedClientId requestedClientId() {
    return requestedClientId;
  }

  public User user() {
    return user;
  }

  public boolean isSameUser(User interactedUser) {
    if (!hasUser()) {
      return false;
    }
    return this.user.sub().equals(interactedUser.sub());
  }

  public AuthenticationContext context() {
    return context;
  }

  public LocalDateTime createdAt() {
    return createdAt;
  }

  public LocalDateTime expiredAt() {
    return expiredAt;
  }

  public boolean hasUser() {
    return user != null && user.exists();
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("authorization_flow", authorizationFlow.value());
    map.put("tenant_id", tenantIdentifier.value());
    map.put("client_id", requestedClientId.value());
    map.put("user", user.toMap());
    map.put("context", context.toMap());
    map.put("created_at", createdAt.toString());
    map.put("expired_at", expiredAt.toString());
    return map;
  }

  public AuthenticationRequest updateWithUser(
      AuthenticationInteractionRequestResult interactionRequestResult) {
    User user = interactionRequestResult.user();
    return new AuthenticationRequest(
        authorizationFlow,
        tenantIdentifier,
        requestedClientId,
        user,
        context,
        createdAt,
        expiredAt);
  }

  public AuthenticationRequest updateWithUser(FederationInteractionResult result) {
    User user = result.user();
    return new AuthenticationRequest(
        authorizationFlow,
        tenantIdentifier,
        requestedClientId,
        user,
        context,
        createdAt,
        expiredAt);
  }

  public AcrValues acrValues() {
    return context.acrValues();
  }

  public Scopes scopes() {
    return context.scopes();
  }
}
