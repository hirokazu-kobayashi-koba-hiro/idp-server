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

package org.idp.server.core.openid.token.service;

import java.util.UUID;
import org.idp.server.core.openid.grant_management.AuthorizationGranted;
import org.idp.server.core.openid.grant_management.AuthorizationGrantedIdentifier;
import org.idp.server.core.openid.grant_management.AuthorizationGrantedRepository;
import org.idp.server.core.openid.grant_management.grant.AuthorizationCodeGrant;
import org.idp.server.core.openid.grant_management.grant.AuthorizationGrant;
import org.idp.server.core.openid.identity.id_token.IdTokenCreator;
import org.idp.server.core.openid.identity.id_token.IdTokenCustomClaims;
import org.idp.server.core.openid.identity.id_token.IdTokenCustomClaimsBuilder;
import org.idp.server.core.openid.oauth.clientauthenticator.clientcredentials.ClientCredentials;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.repository.AuthorizationCodeGrantRepository;
import org.idp.server.core.openid.oauth.repository.AuthorizationRequestRepository;
import org.idp.server.core.openid.oauth.request.AuthorizationRequest;
import org.idp.server.core.openid.oauth.type.oauth.AuthorizationCode;
import org.idp.server.core.openid.oauth.type.oauth.GrantType;
import org.idp.server.core.openid.oauth.type.oidc.IdToken;
import org.idp.server.core.openid.oauth.type.vc.CNonceCreatable;
import org.idp.server.core.openid.oauth.type.verifiablecredential.CNonce;
import org.idp.server.core.openid.oauth.type.verifiablecredential.CNonceExpiresIn;
import org.idp.server.core.openid.token.*;
import org.idp.server.core.openid.token.exception.TokenBadRequestException;
import org.idp.server.core.openid.token.repository.OAuthTokenCommandRepository;
import org.idp.server.core.openid.token.validator.TokenRequestCodeGrantValidator;
import org.idp.server.core.openid.token.verifier.AuthorizationCodeGrantVerifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * 4.1.3. Access Token Request authorization code handling
 *
 * <p>The client makes a request to the token endpoint by sending the following parameters using the
 * "application/x-www-form-urlencoded" format per Appendix B with a character encoding of UTF-8 in
 * the HTTP request entity-body:
 *
 * <p>grant_type REQUIRED. Value MUST be set to "authorization_code".
 *
 * <p>code REQUIRED. The authorization code received from the authorization server.
 *
 * <p>redirect_uri REQUIRED, if the "redirect_uri" parameter was included in the authorization
 * request as described in Section 4.1.1, and their values MUST be identical.
 *
 * <p>client_id REQUIRED, if the client is not authenticating with the authorization server as
 * described in Section 3.2.1.
 *
 * <p>If the client type is confidential or the client was issued client credentials (or assigned
 * other authentication requirements), the client MUST authenticate with the authorization server as
 * described in Section 3.2.1.
 *
 * <p>For example, the client makes the following HTTP request using TLS (with extra line breaks for
 * display purposes only):
 *
 * <p>POST /token HTTP/1.1 Host: server.example.com Authorization: Basic
 * czZCaGRSa3F0MzpnWDFmQmF0M2JW Content-Type: application/x-www-form-urlencoded
 *
 * <p>grant_type=authorization_code&code=SplxlOBeZQQYbYS6WxSbIA
 * &redirect_uri=https%3A%2F%2Fclient%2Eexample%2Ecom%2Fcb
 *
 * <p>The authorization server MUST:
 *
 * <p>o require client authentication for confidential clients or for any client that was issued
 * client credentials (or with other authentication requirements),
 *
 * <p>o authenticate the client if client authentication is included,
 *
 * <p>o ensure that the authorization code was issued to the authenticated confidential client, or
 * if the client is public, ensure that the code was issued to "client_id" in the request,
 *
 * <p>o verify that the authorization code is valid, and
 *
 * <p>o ensure that the "redirect_uri" parameter is present if the "redirect_uri" parameter was
 * included in the initial authorization request as described in Section 4.1.1, and if included
 * ensure that their values are identical.
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc6749#section-4.1.3">4.1.3. Access Token
 *     Request</a>
 */
public class AuthorizationCodeGrantService
    implements OAuthTokenCreationService, RefreshTokenCreatable, CNonceCreatable {

  AuthorizationRequestRepository authorizationRequestRepository;
  OAuthTokenCommandRepository oAuthTokenCommandRepository;
  AuthorizationCodeGrantRepository authorizationCodeGrantRepository;
  AuthorizationGrantedRepository authorizationGrantedRepository;
  AuthorizationCodeGrantVerifier verifier;
  IdTokenCreator idTokenCreator;
  AccessTokenCreator accessTokenCreator;

  public AuthorizationCodeGrantService(
      AuthorizationRequestRepository authorizationRequestRepository,
      OAuthTokenCommandRepository oAuthTokenCommandRepository,
      AuthorizationCodeGrantRepository authorizationCodeGrantRepository,
      AuthorizationGrantedRepository authorizationGrantedRepository) {
    this.authorizationRequestRepository = authorizationRequestRepository;
    this.oAuthTokenCommandRepository = oAuthTokenCommandRepository;
    this.authorizationCodeGrantRepository = authorizationCodeGrantRepository;
    this.authorizationGrantedRepository = authorizationGrantedRepository;
    this.verifier = new AuthorizationCodeGrantVerifier();
    this.idTokenCreator = IdTokenCreator.getInstance();
    this.accessTokenCreator = AccessTokenCreator.getInstance();
  }

  @Override
  public GrantType grantType() {
    return GrantType.authorization_code;
  }

  @Override
  public OAuthToken create(
      TokenRequestContext tokenRequestContext, ClientCredentials clientCredentials) {
    TokenRequestCodeGrantValidator validator =
        new TokenRequestCodeGrantValidator(tokenRequestContext.parameters());
    validator.validate();

    Tenant tenant = tokenRequestContext.tenant();
    AuthorizationCode code = tokenRequestContext.code();
    AuthorizationCodeGrant authorizationCodeGrant =
        authorizationCodeGrantRepository.find(tenant, code);

    if (!authorizationCodeGrant.exists()) {
      throw new TokenBadRequestException("invalid_grant", "not found authorization code.");
    }

    AuthorizationRequest authorizationRequest =
        authorizationRequestRepository.find(
            tenant, authorizationCodeGrant.authorizationRequestIdentifier());

    verifier.verify(
        tokenRequestContext, authorizationRequest, authorizationCodeGrant, clientCredentials);

    AuthorizationServerConfiguration authorizationServerConfiguration =
        tokenRequestContext.serverConfiguration();
    ClientConfiguration clientConfiguration = tokenRequestContext.clientConfiguration();

    AuthorizationGrant authorizationGrant = authorizationCodeGrant.authorizationGrant();
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

    if (authorizationRequest.isOidcProfile()) {
      IdTokenCustomClaims idTokenCustomClaims =
          new IdTokenCustomClaimsBuilder()
              .add(authorizationCodeGrant.authorizationCode())
              .add(accessToken.accessTokenEntity())
              .add(authorizationRequest.nonce())
              .add(authorizationRequest.state())
              .build();
      IdToken idToken =
          idTokenCreator.createIdToken(
              authorizationGrant.user(),
              authorizationCodeGrant.authentication(),
              authorizationGrant,
              idTokenCustomClaims,
              authorizationRequest.requestedClaimsPayload(),
              authorizationServerConfiguration,
              clientConfiguration);
      oAuthTokenBuilder.add(idToken);
    }

    if (authorizationRequest.isVerifiableCredentialRequest()) {
      CNonce cNonce = createCNonce();
      CNonceExpiresIn cNonceExpiresIn = new CNonceExpiresIn(3600L);
      oAuthTokenBuilder.add(cNonce);
      oAuthTokenBuilder.add(cNonceExpiresIn);
    }

    registerOrUpdate(tenant, authorizationGrant);

    OAuthToken oAuthToken = oAuthTokenBuilder.build();

    oAuthTokenCommandRepository.register(tenant, oAuthToken);
    authorizationCodeGrantRepository.delete(tokenRequestContext.tenant(), authorizationCodeGrant);
    authorizationRequestRepository.delete(
        tokenRequestContext.tenant(), authorizationCodeGrant.authorizationRequestIdentifier());

    return oAuthToken;
  }

  private void registerOrUpdate(Tenant tenant, AuthorizationGrant authorizationGrant) {
    AuthorizationGranted latest =
        authorizationGrantedRepository.find(
            tenant, authorizationGrant.requestedClientId(), authorizationGrant.user());

    if (latest.exists()) {
      AuthorizationGranted merge = latest.merge(authorizationGrant);

      authorizationGrantedRepository.update(tenant, merge);
      return;
    }
    AuthorizationGrantedIdentifier authorizationGrantedIdentifier =
        new AuthorizationGrantedIdentifier(UUID.randomUUID().toString());
    AuthorizationGranted authorizationGranted =
        new AuthorizationGranted(authorizationGrantedIdentifier, authorizationGrant);
    authorizationGrantedRepository.register(tenant, authorizationGranted);
  }
}
