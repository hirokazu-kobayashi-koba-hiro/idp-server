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

package org.idp.server.core.extension.ciba.grant;

import java.time.LocalDateTime;
import org.idp.server.basic.type.ciba.AuthReqId;
import org.idp.server.basic.type.ciba.Interval;
import org.idp.server.basic.type.extension.DeniedScopes;
import org.idp.server.basic.type.extension.ExpiresAt;
import org.idp.server.basic.type.oauth.RequestedClientId;
import org.idp.server.basic.type.oauth.Scopes;
import org.idp.server.core.extension.ciba.request.BackchannelAuthenticationRequestIdentifier;
import org.idp.server.core.oidc.authentication.Authentication;
import org.idp.server.core.oidc.client.ClientIdentifier;
import org.idp.server.core.oidc.grant.AuthorizationGrant;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public class CibaGrant {

  BackchannelAuthenticationRequestIdentifier backchannelAuthenticationRequestIdentifier =
      new BackchannelAuthenticationRequestIdentifier();
  AuthorizationGrant authorizationGrant;
  AuthReqId authReqId;
  ExpiresAt expiresAt;
  Interval interval;
  CibaGrantStatus status;

  public CibaGrant() {}

  public CibaGrant(
      BackchannelAuthenticationRequestIdentifier backchannelAuthenticationRequestIdentifier,
      AuthorizationGrant authorizationGrant,
      AuthReqId authReqId,
      ExpiresAt expiresAt,
      Interval interval,
      CibaGrantStatus status) {
    this.backchannelAuthenticationRequestIdentifier = backchannelAuthenticationRequestIdentifier;
    this.authorizationGrant = authorizationGrant;
    this.authReqId = authReqId;
    this.expiresAt = expiresAt;
    this.interval = interval;
    this.status = status;
  }

  public BackchannelAuthenticationRequestIdentifier backchannelAuthenticationRequestIdentifier() {
    return backchannelAuthenticationRequestIdentifier;
  }

  public TenantIdentifier tenantIdentifier() {
    return authorizationGrant.tenantIdentifier();
  }

  public User user() {
    return authorizationGrant.user();
  }

  public AuthorizationGrant authorizationGrant() {
    return authorizationGrant;
  }

  public ClientIdentifier clientIdentifier() {
    return authorizationGrant.clientIdentifier();
  }

  public AuthReqId authReqId() {
    return authReqId;
  }

  public boolean isGrantedClient(ClientIdentifier clientIdentifier) {
    return authorizationGrant.isGranted(clientIdentifier);
  }

  public boolean isExpire(LocalDateTime other) {
    return expiresAt.isExpire(other);
  }

  public boolean exists() {
    return backchannelAuthenticationRequestIdentifier.exists();
  }

  public Scopes scopes() {
    return authorizationGrant.scopes();
  }

  public ExpiresAt expiredAt() {
    return expiresAt;
  }

  public Interval interval() {
    return interval;
  }

  public CibaGrantStatus status() {
    return status;
  }

  public boolean isAuthorizationPending() {
    return status.isAuthorizationPending();
  }

  public boolean isAuthorized() {
    return status.isAuthorized();
  }

  public boolean isAccessDenied() {
    return status.isAccessDenied();
  }

  public RequestedClientId requestedClientId() {
    return authorizationGrant.requestedClientId();
  }

  public CibaGrant updateWith(
      CibaGrantStatus cibaGrantStatus, Authentication authentication, DeniedScopes deniedScopes) {

    Scopes removedScopes = authorizationGrant.scopes().removeScopes(deniedScopes);
    AuthorizationGrant updatedGrant = authorizationGrant.updatedWith(authentication, removedScopes);

    return new CibaGrant(
        backchannelAuthenticationRequestIdentifier,
        updatedGrant,
        authReqId,
        expiresAt,
        interval,
        cibaGrantStatus);
  }
}
