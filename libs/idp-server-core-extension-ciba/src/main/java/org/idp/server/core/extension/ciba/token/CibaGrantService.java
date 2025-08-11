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

package org.idp.server.core.extension.ciba.token;

import java.util.UUID;
import org.idp.server.core.extension.ciba.grant.CibaGrant;
import org.idp.server.core.extension.ciba.repository.BackchannelAuthenticationRequestRepository;
import org.idp.server.core.extension.ciba.repository.CibaGrantRepository;
import org.idp.server.core.extension.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.core.openid.grant_management.AuthorizationGranted;
import org.idp.server.core.openid.grant_management.AuthorizationGrantedIdentifier;
import org.idp.server.core.openid.grant_management.AuthorizationGrantedRepository;
import org.idp.server.core.openid.grant_management.grant.AuthorizationGrant;
import org.idp.server.core.openid.identity.id_token.IdTokenCreator;
import org.idp.server.core.openid.identity.id_token.IdTokenCustomClaims;
import org.idp.server.core.openid.identity.id_token.IdTokenCustomClaimsBuilder;
import org.idp.server.core.openid.identity.id_token.RequestedClaimsPayload;
import org.idp.server.core.openid.oauth.clientauthenticator.clientcredentials.ClientCredentials;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.type.ciba.AuthReqId;
import org.idp.server.core.openid.oauth.type.oauth.GrantType;
import org.idp.server.core.openid.oauth.type.oidc.IdToken;
import org.idp.server.core.openid.token.*;
import org.idp.server.core.openid.token.exception.TokenBadRequestException;
import org.idp.server.core.openid.token.repository.OAuthTokenCommandRepository;
import org.idp.server.core.openid.token.service.OAuthTokenCreationService;
import org.idp.server.core.openid.token.validator.CibaGrantValidator;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class CibaGrantService implements OAuthTokenCreationService, RefreshTokenCreatable {

  BackchannelAuthenticationRequestRepository backchannelAuthenticationRequestRepository;
  CibaGrantRepository cibaGrantRepository;
  OAuthTokenCommandRepository oAuthTokenCommandRepository;
  AuthorizationGrantedRepository authorizationGrantedRepository;
  IdTokenCreator idTokenCreator;
  AccessTokenCreator accessTokenCreator;

  public CibaGrantService(
      BackchannelAuthenticationRequestRepository backchannelAuthenticationRequestRepository,
      CibaGrantRepository cibaGrantRepository,
      OAuthTokenCommandRepository oAuthTokenCommandRepository,
      AuthorizationGrantedRepository authorizationGrantedRepository) {
    this.backchannelAuthenticationRequestRepository = backchannelAuthenticationRequestRepository;
    this.cibaGrantRepository = cibaGrantRepository;
    this.oAuthTokenCommandRepository = oAuthTokenCommandRepository;
    this.authorizationGrantedRepository = authorizationGrantedRepository;
    this.idTokenCreator = IdTokenCreator.getInstance();
    this.accessTokenCreator = AccessTokenCreator.getInstance();
  }

  @Override
  public GrantType grantType() {
    return GrantType.ciba;
  }

  @Override
  public OAuthToken create(
      TokenRequestContext tokenRequestContext, ClientCredentials clientCredentials) {
    CibaGrantValidator validator = new CibaGrantValidator(tokenRequestContext);
    validator.validate();

    Tenant tenant = tokenRequestContext.tenant();
    AuthReqId authReqId = tokenRequestContext.authReqId();
    CibaGrant cibaGrant = cibaGrantRepository.find(tenant, authReqId);

    if (!cibaGrant.exists()) {
      throw new TokenBadRequestException(
          "invalid_grant",
          String.format(
              "auth_req_id is invalid (%s) (%s)",
              authReqId.value(), tokenRequestContext.requestedClientId().value()));
    }

    BackchannelAuthenticationRequest backchannelAuthenticationRequest =
        backchannelAuthenticationRequestRepository.find(
            tenant, cibaGrant.backchannelAuthenticationRequestIdentifier());

    CibaGrantVerifier verifier =
        new CibaGrantVerifier(tokenRequestContext, backchannelAuthenticationRequest, cibaGrant);
    verifier.verify();

    AuthorizationServerConfiguration authorizationServerConfiguration =
        tokenRequestContext.serverConfiguration();
    ClientConfiguration clientConfiguration = tokenRequestContext.clientConfiguration();

    AuthorizationGrant authorizationGrant = cibaGrant.authorizationGrant();
    AccessToken accessToken =
        accessTokenCreator.create(
            authorizationGrant,
            authorizationServerConfiguration,
            clientConfiguration,
            clientCredentials);
    RefreshToken refreshToken =
        createRefreshToken(authorizationServerConfiguration, clientConfiguration);
    OAuthTokenBuilder oAuthTokenBuilder =
        new OAuthTokenBuilder(new OAuthTokenIdentifier(UUID.randomUUID().toString()))
            .add(accessToken)
            .add(refreshToken);
    IdTokenCustomClaims idTokenCustomClaims = new IdTokenCustomClaimsBuilder().build();
    IdToken idToken =
        idTokenCreator.createIdToken(
            cibaGrant.user(),
            cibaGrant.authorizationGrant().authentication(),
            authorizationGrant,
            idTokenCustomClaims,
            new RequestedClaimsPayload(),
            authorizationServerConfiguration,
            clientConfiguration);
    oAuthTokenBuilder.add(idToken);

    registerOrUpdate(tenant, cibaGrant);

    OAuthToken oAuthToken = oAuthTokenBuilder.build();

    oAuthTokenCommandRepository.register(tenant, oAuthToken);
    cibaGrantRepository.delete(tenant, cibaGrant);

    return oAuthToken;
  }

  private void registerOrUpdate(Tenant tenant, CibaGrant cibaGrant) {
    AuthorizationGranted latest =
        authorizationGrantedRepository.find(
            tenant, cibaGrant.requestedClientId(), cibaGrant.user());

    if (latest.exists()) {
      AuthorizationGranted merge = latest.merge(cibaGrant.authorizationGrant());

      authorizationGrantedRepository.update(tenant, merge);
      return;
    }
    AuthorizationGrantedIdentifier authorizationGrantedIdentifier =
        new AuthorizationGrantedIdentifier(UUID.randomUUID().toString());
    AuthorizationGranted authorizationGranted =
        new AuthorizationGranted(authorizationGrantedIdentifier, cibaGrant.authorizationGrant());
    authorizationGrantedRepository.register(tenant, authorizationGranted);
  }
}
