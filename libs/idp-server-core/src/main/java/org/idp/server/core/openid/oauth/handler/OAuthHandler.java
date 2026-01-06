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

package org.idp.server.core.openid.oauth.handler;

import org.idp.server.core.openid.oauth.OAuthUserDelegate;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfigurationQueryRepository;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.core.openid.oauth.io.OAuthLogoutRequest;
import org.idp.server.core.openid.oauth.io.OAuthLogoutResponse;
import org.idp.server.core.openid.oauth.io.OAuthViewDataRequest;
import org.idp.server.core.openid.oauth.io.OAuthViewDataResponse;
import org.idp.server.core.openid.oauth.io.OAuthViewDataStatus;
import org.idp.server.core.openid.oauth.logout.LogoutRequestVerifier;
import org.idp.server.core.openid.oauth.logout.OAuthLogoutContext;
import org.idp.server.core.openid.oauth.logout.OAuthLogoutContextCreator;
import org.idp.server.core.openid.oauth.repository.AuthorizationRequestRepository;
import org.idp.server.core.openid.oauth.request.AuthorizationRequest;
import org.idp.server.core.openid.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.core.openid.oauth.request.OAuthLogoutParameters;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.core.openid.oauth.validator.OAuthLogoutValidator;
import org.idp.server.core.openid.oauth.view.OAuthViewData;
import org.idp.server.core.openid.oauth.view.OAuthViewDataCreator;
import org.idp.server.core.openid.session.ClientSessionIdentifier;
import org.idp.server.core.openid.session.OIDCSessionHandler;
import org.idp.server.core.openid.session.OPSession;
import org.idp.server.core.openid.session.TerminationReason;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class OAuthHandler {

  AuthorizationRequestRepository authorizationRequestRepository;
  AuthorizationServerConfigurationQueryRepository authorizationServerConfigurationQueryRepository;
  ClientConfigurationQueryRepository clientConfigurationQueryRepository;
  OIDCSessionHandler oidcSessionHandler;

  public OAuthHandler(
      AuthorizationRequestRepository authorizationRequestRepository,
      AuthorizationServerConfigurationQueryRepository
          authorizationServerConfigurationQueryRepository,
      ClientConfigurationQueryRepository clientConfigurationQueryRepository,
      OIDCSessionHandler oidcSessionHandler) {
    this.authorizationRequestRepository = authorizationRequestRepository;
    this.authorizationServerConfigurationQueryRepository =
        authorizationServerConfigurationQueryRepository;
    this.clientConfigurationQueryRepository = clientConfigurationQueryRepository;
    this.oidcSessionHandler = oidcSessionHandler;
  }

  public OAuthViewDataResponse handleViewData(
      OAuthViewDataRequest request, OAuthUserDelegate oAuthUserDelegate) {
    Tenant tenant = request.tenant();
    AuthorizationRequestIdentifier authorizationRequestIdentifier = request.toIdentifier();

    AuthorizationRequest authorizationRequest =
        authorizationRequestRepository.get(tenant, authorizationRequestIdentifier);
    RequestedClientId requestedClientId = authorizationRequest.requestedClientId();
    AuthorizationServerConfiguration authorizationServerConfiguration =
        authorizationServerConfigurationQueryRepository.get(tenant);
    ClientConfiguration clientConfiguration =
        clientConfigurationQueryRepository.get(tenant, requestedClientId);

    OPSession opSession = request.opSession();

    // Check if user still exists in DB
    if (opSession != null && opSession.exists()) {
      boolean userExists = oAuthUserDelegate.userExists(tenant, opSession.userIdentifier());
      if (!userExists) {
        opSession = null;
      }
    }

    OAuthViewDataCreator creator =
        new OAuthViewDataCreator(
            authorizationRequest,
            authorizationServerConfiguration,
            clientConfiguration,
            opSession,
            request.additionalViewData());
    OAuthViewData oAuthViewData = creator.create();

    return new OAuthViewDataResponse(OAuthViewDataStatus.OK, oAuthViewData);
  }

  public AuthorizationRequest handleGettingData(
      Tenant tenant, AuthorizationRequestIdentifier authorizationRequestIdentifier) {
    return authorizationRequestRepository.get(tenant, authorizationRequestIdentifier);
  }

  /**
   * Handles RP-Initiated Logout requests.
   *
   * <p>OpenID Connect RP-Initiated Logout 1.0 implementation.
   *
   * <p>Note: idp-server requires id_token_hint for all logout requests. This ensures that the
   * End-User can always be identified without requiring a confirmation screen.
   *
   * @param request the logout request
   * @return the logout response
   * @see <a href="https://openid.net/specs/openid-connect-rpinitiated-1_0.html">RP-Initiated
   *     Logout</a>
   */
  public OAuthLogoutResponse handleLogout(OAuthLogoutRequest request) {

    OAuthLogoutParameters parameters = request.toParameters();
    Tenant tenant = request.tenant();

    // 1. Validate parameters (id_token_hint is REQUIRED)
    OAuthLogoutValidator validator = new OAuthLogoutValidator(tenant, parameters);
    validator.validate();

    // 2. Get server configuration
    AuthorizationServerConfiguration serverConfiguration =
        authorizationServerConfigurationQueryRepository.get(tenant);

    // 3. Create context (validates id_token_hint, determines client, builds context)
    OAuthLogoutContextCreator contextCreator =
        new OAuthLogoutContextCreator(
            tenant, parameters, serverConfiguration, clientConfigurationQueryRepository);
    OAuthLogoutContext context = contextCreator.create();

    // 4. Verify request (post_logout_redirect_uri, audience match, etc.)
    LogoutRequestVerifier verifier = new LogoutRequestVerifier();
    verifier.verify(context);

    // 5. Terminate OPSession using sid from id_token_hint
    if (context.hasSessionId()) {
      ClientSessionIdentifier clientSessionId = new ClientSessionIdentifier(context.sessionId());
      oidcSessionHandler
          .findOPSessionBySid(tenant, clientSessionId)
          .ifPresent(
              opSessionId ->
                  oidcSessionHandler.terminateOPSession(
                      tenant, opSessionId, TerminationReason.USER_LOGOUT));
    }

    // 6. Build response
    if (context.hasPostLogoutRedirectUri()) {
      String redirectUri = context.postLogoutRedirectUri().value();
      if (context.hasState()) {
        redirectUri += "?state=" + context.state().value();
      }
      return OAuthLogoutResponse.redirect(redirectUri, context);
    }

    return OAuthLogoutResponse.ok(context);
  }
}
