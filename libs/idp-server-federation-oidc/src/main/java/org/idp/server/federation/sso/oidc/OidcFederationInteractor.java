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

package org.idp.server.federation.sso.oidc;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.basic.jose.JoseContext;
import org.idp.server.basic.jose.JoseHandler;
import org.idp.server.basic.jose.JoseInvalidException;
import org.idp.server.core.oidc.federation.*;
import org.idp.server.core.oidc.federation.io.*;
import org.idp.server.core.oidc.federation.repository.FederationConfigurationQueryRepository;
import org.idp.server.core.oidc.federation.sso.*;
import org.idp.server.core.oidc.federation.sso.oidc.OidcSsoSession;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.identity.repository.UserQueryRepository;
import org.idp.server.core.oidc.request.AuthorizationRequestIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class OidcFederationInteractor implements FederationInteractor {

  FederationConfigurationQueryRepository configurationQueryRepository;
  SsoSessionCommandRepository sessionCommandRepository;
  SsoSessionQueryRepository sessionQueryRepository;
  OidcSsoExecutors oidcSsoExecutors;

  public OidcFederationInteractor(
      OidcSsoExecutors oidcSsoExecutors,
      FederationConfigurationQueryRepository configurationQueryRepository,
      SsoSessionCommandRepository sessionCommandRepository,
      SsoSessionQueryRepository sessionQueryRepository) {
    this.oidcSsoExecutors = oidcSsoExecutors;
    this.configurationQueryRepository = configurationQueryRepository;
    this.sessionCommandRepository = sessionCommandRepository;
    this.sessionQueryRepository = sessionQueryRepository;
  }

  public FederationRequestResponse request(
      Tenant tenant,
      AuthorizationRequestIdentifier authorizationRequestIdentifier,
      FederationType federationType,
      SsoProvider ssoProvider) {

    OidcSsoConfiguration oidcSsoConfiguration =
        configurationQueryRepository.get(
            tenant, federationType, ssoProvider, OidcSsoConfiguration.class);

    OidcSsoSessionCreator authorizationRequestCreator =
        new OidcSsoSessionCreator(
            oidcSsoConfiguration,
            tenant,
            authorizationRequestIdentifier,
            federationType,
            ssoProvider);
    OidcSsoSession oidcSsoSession = authorizationRequestCreator.create();

    sessionCommandRepository.register(
        tenant, oidcSsoSession.ssoSessionIdentifier(), oidcSsoSession);

    Map<String, Object> contents = new HashMap<>();
    contents.put("redirect_uri", oidcSsoSession.authorizationRequestUri());
    return new FederationRequestResponse(FederationRequestStatus.REDIRECABLE_OK, contents);
  }

  public FederationInteractionResult callback(
      Tenant tenant,
      FederationType federationType,
      SsoProvider ssoProvider,
      FederationCallbackRequest federationCallbackRequest,
      UserQueryRepository userQueryRepository) {

    SsoState ssoState = federationCallbackRequest.ssoState();
    FederationCallbackParameters parameters = federationCallbackRequest.parameters();
    OidcSsoSession session =
        sessionQueryRepository.get(tenant, ssoState.ssoSessionIdentifier(), OidcSsoSession.class);

    OidcSsoConfiguration oidcSsoConfiguration =
        configurationQueryRepository.get(
            tenant, federationType, ssoProvider, OidcSsoConfiguration.class);

    OidcSsoExecutor oidcSsoExecutor = oidcSsoExecutors.get(oidcSsoConfiguration.ssoProvider());

    OidcTokenRequestCreator tokenRequestCreator =
        new OidcTokenRequestCreator(parameters, session, oidcSsoConfiguration);
    OidcTokenRequest tokenRequest = tokenRequestCreator.create();
    OidcTokenResponse tokenResponse = oidcSsoExecutor.requestToken(tokenRequest);

    JoseContext joseContext =
        verifyAndParseIdToken(oidcSsoExecutor, oidcSsoConfiguration, tokenResponse);

    OidcUserinfoRequest userinfoRequest =
        new OidcUserinfoRequest(
            oidcSsoConfiguration.userinfoEndpoint(), tokenResponse.accessToken());
    OidcUserinfoResponse userinfoResponse = oidcSsoExecutor.requestUserInfo(userinfoRequest);

    User existingUser =
        userQueryRepository.findByProvider(
            tenant, oidcSsoConfiguration.issuerName(), userinfoResponse.sub());

    OidcUserinfoResponseConvertor convertor =
        new OidcUserinfoResponseConvertor(existingUser, userinfoResponse, oidcSsoConfiguration);
    User user = convertor.convert();

    sessionCommandRepository.delete(tenant, session.ssoSessionIdentifier());

    return FederationInteractionResult.success(federationType, ssoProvider, session, user);
  }

  private JoseContext verifyAndParseIdToken(
      OidcSsoExecutor oidcSsoExecutor,
      OidcSsoConfiguration configuration,
      OidcTokenResponse tokenResponse) {
    try {
      OidcJwksResponse jwksResponse =
          oidcSsoExecutor.getJwks(new OidcJwksRequest(configuration.jwksUri()));

      JoseHandler joseHandler = new JoseHandler();
      JoseContext joseContext =
          joseHandler.handle(tokenResponse.idToken(), jwksResponse.value(), "", "");

      joseContext.verifySignature();

      return joseContext;
    } catch (JoseInvalidException e) {

      throw new OidcInvalidIdTokenException("failed to parse id_token", e);
    }
  }
}
