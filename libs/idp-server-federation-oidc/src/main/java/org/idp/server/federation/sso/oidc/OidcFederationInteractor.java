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
import java.util.UUID;
import org.idp.server.core.openid.federation.*;
import org.idp.server.core.openid.federation.io.*;
import org.idp.server.core.openid.federation.repository.FederationConfigurationQueryRepository;
import org.idp.server.core.openid.federation.sso.*;
import org.idp.server.core.openid.federation.sso.oidc.OidcSsoSession;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.mapper.UserInfoMapper;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.core.openid.oauth.request.AuthorizationRequestIdentifier;
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
    OidcTokenResult tokenResult = oidcSsoExecutor.requestToken(tokenRequest);

    if (tokenResult.isError()) {

      return FederationInteractionResult.serverError(
          federationType, ssoProvider, session, tokenResult.bodyAsMap());
    }

    OidcJwksResult jwksResult =
        oidcSsoExecutor.getJwks(new OidcJwksRequest(oidcSsoConfiguration.jwksUri()));

    if (jwksResult.isError()) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "server_error");
      response.put("error_description", jwksResult.body());

      return FederationInteractionResult.serverError(
          federationType, ssoProvider, session, response);
    }

    IdTokenVerificationResult idTokenVerificationResult =
        oidcSsoExecutor.verifyIdToken(oidcSsoConfiguration, session, jwksResult, tokenResult);
    if (idTokenVerificationResult.isError()) {

      return FederationInteractionResult.serverError(
          federationType, ssoProvider, session, idTokenVerificationResult.data());
    }

    OidcUserinfoRequest userinfoRequest =
        new OidcUserinfoRequest(
            oidcSsoConfiguration.userinfoEndpoint(),
            tokenResult.accessToken(),
            oidcSsoConfiguration.userinfoExecution());
    UserinfoExecutionResult userinfoResult = oidcSsoExecutor.requestUserInfo(userinfoRequest);

    if (userinfoResult.isClientError()) {
      return FederationInteractionResult.serverError(
          federationType, ssoProvider, session, userinfoResult.contents());
    }

    UserInfoMapper userInfoMapper =
        new UserInfoMapper(
            oidcSsoConfiguration.userinfoMappingRules(),
            userinfoResult.contents(),
            oidcSsoConfiguration.issuerName());
    User user = userInfoMapper.toUser();

    User exsitingUser =
        userQueryRepository.findByExternalIdpSubject(
            tenant, user.externalUserId(), oidcSsoConfiguration.issuerName());

    if (exsitingUser.exists()) {
      user.setSub(exsitingUser.sub());
    } else {
      user.setSub(UUID.randomUUID().toString());
    }

    sessionCommandRepository.delete(tenant, session.ssoSessionIdentifier());

    return FederationInteractionResult.success(federationType, ssoProvider, session, user);
  }
}
