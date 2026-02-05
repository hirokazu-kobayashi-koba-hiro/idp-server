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

package org.idp.server.core.openid.authentication;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.openid.federation.FederationInteractionResult;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.device.AuthenticationDevice;
import org.idp.server.core.openid.oauth.configuration.client.ClientAttributes;
import org.idp.server.core.openid.oauth.type.AuthFlow;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.core.openid.oauth.type.oauth.Scopes;
import org.idp.server.core.openid.oauth.type.oidc.AcrValues;
import org.idp.server.platform.multi_tenancy.tenant.TenantAttributes;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public class AuthenticationRequest {

  AuthFlow authFlow;
  TenantIdentifier tenantIdentifier;
  TenantAttributes tenantAttributes;
  RequestedClientId requestedClientId;
  ClientAttributes clientAttributes;
  User user;
  AuthenticationDevice authenticationDevice;
  AuthenticationContext context;
  LocalDateTime createdAt;
  LocalDateTime expiresAt;

  public AuthenticationRequest() {}

  public AuthenticationRequest(
      AuthFlow authFlow,
      TenantIdentifier tenantIdentifier,
      TenantAttributes tenantAttributes,
      RequestedClientId requestedClientId,
      ClientAttributes clientAttributes,
      User user,
      AuthenticationDevice authenticationDevice,
      AuthenticationContext context,
      LocalDateTime createdAt,
      LocalDateTime expiresAt) {
    this.authFlow = authFlow;
    this.tenantIdentifier = tenantIdentifier;
    this.tenantAttributes = tenantAttributes;
    this.requestedClientId = requestedClientId;
    this.clientAttributes = clientAttributes;
    this.user = user;
    this.authenticationDevice = authenticationDevice;
    this.context = context;
    this.createdAt = createdAt;
    this.expiresAt = expiresAt;
  }

  public AuthFlow authFlow() {
    return authFlow;
  }

  public TenantIdentifier tenantIdentifier() {
    return tenantIdentifier;
  }

  public TenantAttributes tenantAttributes() {
    return tenantAttributes;
  }

  public RequestedClientId requestedClientId() {
    return requestedClientId;
  }

  public ClientAttributes clientAttributes() {
    return clientAttributes;
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

  public AuthenticationDevice authenticationDevice() {
    return authenticationDevice;
  }

  public AuthenticationContext context() {
    return context;
  }

  public LocalDateTime createdAt() {
    return createdAt;
  }

  public LocalDateTime expiredAt() {
    return expiresAt;
  }

  public boolean hasUser() {
    return user != null && user.exists();
  }

  public boolean hasAuthenticationDevice() {
    return authenticationDevice != null && authenticationDevice.exists();
  }

  public boolean hasContext() {
    return context != null && context.exists();
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("flow", authFlow.name());
    map.put("tenant_id", tenantIdentifier.value());
    map.put("tenant_attributes", tenantAttributes.toMap());
    map.put("client_id", requestedClientId.value());
    map.put("client_attributes", clientAttributes.toMap());
    if (hasUser()) map.put("user", user.toMap());
    if (hasAuthenticationDevice()) map.put("authentication_device", authenticationDevice.toMap());
    if (hasContext()) map.put("context", context.toMap());
    map.put("created_at", createdAt.toString());
    map.put("expires_at", expiresAt.toString());
    return map;
  }

  public Map<String, Object> toMapForPublic() {
    return toMapForPublic(true);
  }

  /**
   * Converts this request to a map for public API response.
   *
   * <p>When {@code isDeviceAuthenticated} is false, sensitive context information (scopes,
   * acr_values, binding_message, authorization_details) is excluded from the response. This
   * prevents information disclosure when the device has not been authenticated.
   *
   * @param isDeviceAuthenticated true if device authentication was successfully performed
   * @return map representation for public API response
   */
  public Map<String, Object> toMapForPublic(boolean isDeviceAuthenticated) {
    Map<String, Object> map = new HashMap<>();
    map.put("flow", authFlow.name());
    map.put("tenant_id", tenantIdentifier.value());
    map.put("tenant_attributes", tenantAttributes.toMap());
    map.put("client_id", requestedClientId.value());
    map.put("client_attributes", clientAttributes.toMap());
    if (hasUser()) map.put("user", user.toMinimalizedMap());
    if (hasAuthenticationDevice()) map.put("authentication_device", authenticationDevice.toMap());
    // SECURITY: Only include context when device is authenticated
    if (isDeviceAuthenticated && hasContext()) map.put("context", context.toMap());
    map.put("created_at", createdAt.toString());
    map.put("expires_at", expiresAt.toString());
    return map;
  }

  public AuthenticationRequest updateWithUser(
      AuthenticationInteractionRequestResult interactionRequestResult) {
    User user = interactionRequestResult.user();
    return new AuthenticationRequest(
        authFlow,
        tenantIdentifier,
        tenantAttributes,
        requestedClientId,
        clientAttributes,
        user,
        authenticationDevice,
        context,
        createdAt,
        expiresAt);
  }

  public AuthenticationRequest updateWithUser(FederationInteractionResult result) {
    User user = result.user();
    return new AuthenticationRequest(
        authFlow,
        tenantIdentifier,
        tenantAttributes,
        requestedClientId,
        clientAttributes,
        user,
        authenticationDevice,
        context,
        createdAt,
        expiresAt);
  }

  public AcrValues acrValues() {
    return context.acrValues();
  }

  public Scopes scopes() {
    return context.scopes();
  }
}
