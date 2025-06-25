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

package org.idp.server.core.oidc.federation;

import java.util.Map;
import java.util.Objects;
import org.idp.server.core.oidc.authentication.Authentication;
import org.idp.server.core.oidc.federation.sso.SsoProvider;
import org.idp.server.core.oidc.federation.sso.oidc.OidcSsoSession;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.request.AuthorizationRequestIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.security.event.DefaultSecurityEventType;
import org.idp.server.platform.security.event.SecurityEventType;

public class FederationInteractionResult {

  FederationType federationType;
  SsoProvider ssoProvider;
  AuthorizationRequestIdentifier authorizationRequestIdentifier;
  TenantIdentifier tenantIdentifier;
  FederationInteractionStatus status;
  User user;
  Authentication authentication;
  Map<String, Object> response;
  SecurityEventType eventType;

  public static FederationInteractionResult serverError(
      FederationType federationType,
      SsoProvider ssoProvider,
      OidcSsoSession session,
      Map<String, Object> response) {

    AuthorizationRequestIdentifier authorizationRequestIdentifier =
        new AuthorizationRequestIdentifier(session.authorizationRequestId());
    FederationInteractionStatus status = FederationInteractionStatus.SERVER_ERROR;
    TenantIdentifier tenantIdentifier = new TenantIdentifier(session.tenantId());
    DefaultSecurityEventType eventType = DefaultSecurityEventType.federation_failure;

    return new FederationInteractionResult(
        federationType,
        ssoProvider,
        authorizationRequestIdentifier,
        tenantIdentifier,
        status,
        new User(),
        new Authentication(),
        response,
        eventType);
  }

  public static FederationInteractionResult success(
      FederationType federationType, SsoProvider ssoProvider, OidcSsoSession session, User user) {

    AuthorizationRequestIdentifier authorizationRequestIdentifier =
        new AuthorizationRequestIdentifier(session.authorizationRequestId());
    FederationInteractionStatus status = FederationInteractionStatus.SUCCESS;
    Authentication authentication = new Authentication();
    Map<String, Object> response =
        Map.of("id", session.authorizationRequestId(), "tenant_id", session.tenantId());

    TenantIdentifier tenantIdentifier = new TenantIdentifier(session.tenantId());
    DefaultSecurityEventType eventType = DefaultSecurityEventType.federation_success;

    return new FederationInteractionResult(
        federationType,
        ssoProvider,
        authorizationRequestIdentifier,
        tenantIdentifier,
        status,
        user,
        authentication,
        response,
        eventType);
  }

  private FederationInteractionResult(
      FederationType federationType,
      SsoProvider ssoProvider,
      AuthorizationRequestIdentifier authorizationRequestIdentifier,
      TenantIdentifier tenantIdentifier,
      FederationInteractionStatus status,
      User user,
      Authentication authentication,
      Map<String, Object> response,
      DefaultSecurityEventType eventType) {
    this.federationType = federationType;
    this.ssoProvider = ssoProvider;
    this.authorizationRequestIdentifier = authorizationRequestIdentifier;
    this.tenantIdentifier = tenantIdentifier;
    this.status = status;
    this.user = user;
    this.authentication = authentication;
    this.response = response;
    this.eventType = eventType.toEventType();
  }

  public FederationInteractionStatus status() {
    return status;
  }

  public boolean isSuccess() {
    return status.isSuccess();
  }

  public boolean isError() {
    return status.isError();
  }

  public User user() {
    return user;
  }

  public Authentication authentication() {
    return authentication;
  }

  public Map<String, Object> response() {
    return response;
  }

  public SecurityEventType eventType() {
    return eventType;
  }

  public boolean hasUser() {
    return Objects.nonNull(user) && user.exists();
  }

  public boolean hasAuthentication() {
    return Objects.nonNull(authentication) && authentication.exists();
  }

  public AuthorizationRequestIdentifier authorizationRequestIdentifier() {
    return authorizationRequestIdentifier;
  }

  public TenantIdentifier tenantIdentifier() {
    return tenantIdentifier;
  }

  // TODO more consider
  public String interactionTypeName() {
    return federationType.name() + "-" + ssoProvider.name();
  }
}
