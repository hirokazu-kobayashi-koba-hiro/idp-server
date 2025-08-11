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

package org.idp.server.core.openid.grant_management.grant;

import java.time.LocalDateTime;
import java.util.Objects;
import org.idp.server.core.openid.authentication.Authentication;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.oauth.configuration.client.ClientAttributes;
import org.idp.server.core.openid.oauth.configuration.client.ClientIdentifier;
import org.idp.server.core.openid.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.core.openid.oauth.type.extension.ExpiresAt;
import org.idp.server.core.openid.oauth.type.oauth.AuthorizationCode;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.core.openid.oauth.type.oauth.Scopes;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

/** AuthorizationCodeGrant */
public class AuthorizationCodeGrant {

  AuthorizationRequestIdentifier authorizationRequestIdentifier =
      new AuthorizationRequestIdentifier("");
  AuthorizationGrant authorizationGrant;
  AuthorizationCode authorizationCode;
  ExpiresAt expiresAt;

  public AuthorizationCodeGrant() {}

  public AuthorizationCodeGrant(
      AuthorizationRequestIdentifier authorizationRequestIdentifier,
      AuthorizationGrant authorizationGrant,
      AuthorizationCode authorizationCode,
      ExpiresAt expiresAt) {
    this.authorizationRequestIdentifier = authorizationRequestIdentifier;
    this.authorizationGrant = authorizationGrant;
    this.authorizationCode = authorizationCode;
    this.expiresAt = expiresAt;
  }

  public AuthorizationRequestIdentifier authorizationRequestIdentifier() {
    return authorizationRequestIdentifier;
  }

  public User user() {
    return authorizationGrant.user();
  }

  public AuthorizationGrant authorizationGrant() {
    return authorizationGrant;
  }

  public AuthorizationCode authorizationCode() {
    return authorizationCode;
  }

  public boolean isGrantedClient(ClientIdentifier clientIdentifier) {
    return authorizationGrant.isGranted(clientIdentifier);
  }

  public boolean isExpire(LocalDateTime other) {
    return expiresAt.isExpire(other);
  }

  public boolean exists() {
    return Objects.nonNull(authorizationCode) && authorizationCode.exists();
  }

  public Scopes scopes() {
    return authorizationGrant.scopes();
  }

  public Authentication authentication() {
    return authorizationGrant.authentication();
  }

  public RequestedClientId clientId() {
    return authorizationGrant.requestedClientId();
  }

  public ExpiresAt expiredAt() {
    return expiresAt;
  }

  public ClientAttributes clientAttributes() {
    return authorizationGrant.clientAttributes();
  }

  public TenantIdentifier tenantIdentifier() {
    return authorizationGrant.tenantIdentifier();
  }
}
