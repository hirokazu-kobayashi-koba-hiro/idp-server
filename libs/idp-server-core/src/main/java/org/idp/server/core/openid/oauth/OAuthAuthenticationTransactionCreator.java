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

package org.idp.server.core.openid.oauth;

import java.time.LocalDateTime;
import java.util.UUID;
import org.idp.server.core.openid.authentication.AuthSessionId;
import org.idp.server.core.openid.authentication.AuthenticationContext;
import org.idp.server.core.openid.authentication.AuthenticationCustomParams;
import org.idp.server.core.openid.authentication.AuthenticationRequest;
import org.idp.server.core.openid.authentication.AuthenticationTransaction;
import org.idp.server.core.openid.authentication.AuthenticationTransactionAttributes;
import org.idp.server.core.openid.authentication.AuthenticationTransactionIdentifier;
import org.idp.server.core.openid.authentication.AuthorizationIdentifier;
import org.idp.server.core.openid.authentication.policy.AuthenticationPolicy;
import org.idp.server.core.openid.authentication.policy.AuthenticationPolicyConfiguration;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.device.AuthenticationDevice;
import org.idp.server.core.openid.oauth.configuration.client.ClientAttributes;
import org.idp.server.core.openid.oauth.io.OAuthRequestResponse;
import org.idp.server.core.openid.oauth.rar.AuthorizationDetails;
import org.idp.server.core.openid.oauth.request.AuthorizationRequest;
import org.idp.server.core.openid.oauth.type.StandardAuthFlow;
import org.idp.server.core.openid.oauth.type.ciba.BindingMessage;
import org.idp.server.core.openid.oauth.type.oauth.ClientAuthenticationType;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantAttributes;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public class OAuthAuthenticationTransactionCreator {

  /**
   * Creates an AuthenticationTransaction with browser session binding.
   *
   * @param tenant the tenant
   * @param requestResponse the OAuth request response
   * @param policyConfiguration the authentication policy configuration
   * @param authSessionId the authentication session ID for browser binding (prevents session
   *     fixation attacks)
   * @return the created AuthenticationTransaction
   */
  public static AuthenticationTransaction create(
      Tenant tenant,
      OAuthRequestResponse requestResponse,
      AuthenticationPolicyConfiguration policyConfiguration,
      AuthSessionId authSessionId,
      User resolvedUser) {

    AuthenticationTransactionIdentifier identifier =
        new AuthenticationTransactionIdentifier(UUID.randomUUID().toString());
    AuthorizationIdentifier authorizationIdentifier =
        new AuthorizationIdentifier(requestResponse.authorizationRequestIdentifier().value());

    AuthenticationRequest authenticationRequest =
        toAuthenticationRequest(tenant, requestResponse, resolvedUser);
    AuthenticationPolicy authenticationPolicy =
        policyConfiguration.findSatisfiedAuthenticationPolicy(
            authenticationRequest.requestedClientId(),
            authenticationRequest.acrValues(),
            authenticationRequest.scopes());
    AuthenticationTransactionAttributes attributes =
        AuthenticationTransactionAttributes.withAuthSessionId(authSessionId);

    return new AuthenticationTransaction(
        identifier,
        authorizationIdentifier,
        authenticationRequest,
        authenticationPolicy,
        attributes);
  }

  private static AuthenticationRequest toAuthenticationRequest(
      Tenant tenant, OAuthRequestResponse requestResponse, User resolvedUser) {

    AuthorizationRequest authorizationRequest = requestResponse.authorizationRequest();
    StandardAuthFlow standardAuthFlow = StandardAuthFlow.OAUTH;
    TenantIdentifier tenantIdentifier = tenant.identifier();
    TenantAttributes tenantAttributes = tenant.attributes();

    RequestedClientId requestedClientId = authorizationRequest.requestedClientId();
    ClientAttributes clientAttributes = authorizationRequest.clientAttributes();
    User user = resolvedUser;
    AuthenticationDevice authenticationDevice =
        resolvedUser.exists()
            ? resolvedUser.findPrimaryAuthenticationDevice()
            : new AuthenticationDevice();
    AuthorizationDetails authorizationDetails =
        requestResponse.authorizationRequest().authorizationDetails();
    AuthenticationContext context =
        new AuthenticationContext(
            authorizationRequest.acrValues(),
            authorizationRequest.scopes(),
            new BindingMessage(),
            authorizationDetails,
            customParamsOf(requestResponse));
    LocalDateTime createdAt = SystemDateTime.now();
    LocalDateTime expiredAt =
        createdAt.plusSeconds(requestResponse.oauthAuthorizationRequestExpiresIn());
    return new AuthenticationRequest(
        standardAuthFlow.toAuthFlow(),
        tenantIdentifier,
        tenantAttributes,
        requestedClientId,
        clientAttributes,
        user,
        authenticationDevice,
        context,
        createdAt,
        expiredAt);
  }

  /**
   * Issue #1907: the custom parameters, with what each can be trusted as. Decided here, at the
   * authorization endpoint, because it is the last point where the sources are known. A client with
   * no registered authentication method is not taken as authenticating.
   */
  private static AuthenticationCustomParams customParamsOf(OAuthRequestResponse requestResponse) {
    String method = requestResponse.clientConfiguration().tokenEndpointAuthMethod();
    boolean clientAuthenticated =
        method != null && !method.equals(ClientAuthenticationType.none.name());
    return AuthenticationCustomParams.of(
        requestResponse.authorizationRequest().customParams(),
        requestResponse.isPushedRequest(),
        clientAuthenticated,
        requestResponse.isRequestObjectSigned());
  }
}
