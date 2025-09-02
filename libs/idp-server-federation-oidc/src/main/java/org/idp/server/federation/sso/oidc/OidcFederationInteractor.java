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

import java.time.LocalDateTime;
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
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class OidcFederationInteractor implements FederationInteractor {

  FederationConfigurationQueryRepository configurationQueryRepository;
  SsoSessionCommandRepository sessionCommandRepository;
  SsoSessionQueryRepository sessionQueryRepository;
  SsoCredentialsCommandRepository ssoCredentialsCommandRepository;
  OidcSsoExecutors oidcSsoExecutors;
  LoggerWrapper log = LoggerWrapper.getLogger(OidcFederationInteractor.class);

  public OidcFederationInteractor(
      OidcSsoExecutors oidcSsoExecutors,
      FederationConfigurationQueryRepository configurationQueryRepository,
      SsoSessionCommandRepository sessionCommandRepository,
      SsoSessionQueryRepository sessionQueryRepository,
      SsoCredentialsCommandRepository ssoCredentialsCommandRepository) {
    this.oidcSsoExecutors = oidcSsoExecutors;
    this.configurationQueryRepository = configurationQueryRepository;
    this.sessionCommandRepository = sessionCommandRepository;
    this.sessionQueryRepository = sessionQueryRepository;
    this.ssoCredentialsCommandRepository = ssoCredentialsCommandRepository;
  }

  public FederationRequestResponse request(
      Tenant tenant,
      AuthorizationRequestIdentifier authorizationRequestIdentifier,
      FederationType federationType,
      SsoProvider ssoProvider) {

    OidcSsoConfiguration oidcSsoConfiguration =
        configurationQueryRepository.get(
            tenant, federationType, ssoProvider, OidcSsoConfiguration.class);

    OidcSsoExecutor oidcSsoExecutor = oidcSsoExecutors.get(oidcSsoConfiguration.ssoProvider());
    OidcSsoSession oidcSsoSession =
        oidcSsoExecutor.createOidcSession(
            tenant,
            authorizationRequestIdentifier,
            oidcSsoConfiguration,
            federationType,
            ssoProvider);

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

    FederationCallbackParameters parameters = federationCallbackRequest.parameters();

    if (!parameters.hasCode() || parameters.hasError()) {
      log.warn("Error occurred while federation callback. tenantId: {}", tenant.identifierValue());
      Map<String, Object> errors = new HashMap<>();
      errors.put("error", parameters.error());
      errors.put("error_description", parameters.errorDescription());
      return FederationInteractionResult.error(
          federationType, ssoProvider, new OidcSsoSession(), 400, errors);
    }

    SsoState ssoState = federationCallbackRequest.ssoState();
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
      log.error(
          "Error occurred while executing token request. tenantId: {}", tenant.identifierValue());
      return FederationInteractionResult.error(
          federationType, ssoProvider, session, tokenResult.statusCode(), tokenResult.bodyAsMap());
    }

    OidcJwksResult jwksResult =
        oidcSsoExecutor.getJwks(new OidcJwksRequest(oidcSsoConfiguration.jwksUri()));

    if (jwksResult.isError()) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "server_error");
      response.put("error_description", jwksResult.body());

      log.error(
          "Error occurred while executing jwk request. tenantId: {}", tenant.identifierValue());
      return FederationInteractionResult.error(
          federationType, ssoProvider, session, jwksResult.statusCode(), response);
    }

    IdTokenVerificationResult idTokenVerificationResult =
        oidcSsoExecutor.verifyIdToken(oidcSsoConfiguration, session, jwksResult, tokenResult);
    if (idTokenVerificationResult.isError()) {

      log.error(
          "Error occurred while executing id_token validation. tenantId: {}",
          tenant.identifierValue());
      return FederationInteractionResult.error(
          federationType, ssoProvider, session, 400, idTokenVerificationResult.data());
    }

    OidcUserinfoRequest userinfoRequest =
        new OidcUserinfoRequest(
            oidcSsoConfiguration.userinfoEndpoint(),
            tokenResult.accessToken(),
            oidcSsoConfiguration.userinfoExecution());
    UserinfoExecutionResult userinfoResult = oidcSsoExecutor.requestUserInfo(userinfoRequest);

    if (userinfoResult.isError()) {

      log.error(
          "Error occurred while executing userinfo request. tenantId: {}",
          tenant.identifierValue());
      return FederationInteractionResult.error(
          federationType,
          ssoProvider,
          session,
          userinfoResult.statusCode(),
          userinfoResult.contents());
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

    if (oidcSsoConfiguration.isStoreCredentials()) {
      log.debug("Storing credential data into session. tenantId: {}", tenant.identifierValue());
      String provider = oidcSsoConfiguration.provider();
      String scope = oidcSsoConfiguration.scopeAsString();
      String accessToken = tokenResult.accessToken();
      String refreshToken = tokenResult.refreshToken();
      long accessTokenExpiresIn = tokenResult.expiresIn();
      LocalDateTime accessTokenExpiresAt = SystemDateTime.now().plusSeconds(accessTokenExpiresIn);
      long refreshTokenExpiresIn = oidcSsoConfiguration.refreshTokenExpiresIn();
      LocalDateTime refreshTokenExpiresAt = SystemDateTime.now().plusSeconds(refreshTokenExpiresIn);
      SsoCredentials ssoCredentials =
          new SsoCredentials(
              provider,
              scope,
              accessToken,
              refreshToken,
              accessTokenExpiresIn,
              accessTokenExpiresAt,
              refreshTokenExpiresIn,
              refreshTokenExpiresAt);
      ssoCredentialsCommandRepository.register(tenant, user, ssoCredentials);
    }

    return FederationInteractionResult.success(federationType, ssoProvider, session, user);
  }
}
