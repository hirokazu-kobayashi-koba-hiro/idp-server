package org.idp.server.token.service;

import java.util.UUID;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.grantmangment.AuthorizationGranted;
import org.idp.server.grantmangment.AuthorizationGrantedIdentifier;
import org.idp.server.grantmangment.AuthorizationGrantedRepository;
import org.idp.server.oauth.authentication.Authentication;
import org.idp.server.oauth.grant.AuthorizationCodeGrant;
import org.idp.server.oauth.grant.AuthorizationGrant;
import org.idp.server.oauth.identity.IdTokenCreatable;
import org.idp.server.oauth.identity.IdTokenCustomClaims;
import org.idp.server.oauth.identity.IdTokenCustomClaimsBuilder;
import org.idp.server.oauth.repository.AuthorizationCodeGrantRepository;
import org.idp.server.oauth.repository.AuthorizationRequestRepository;
import org.idp.server.oauth.request.AuthorizationRequest;
import org.idp.server.oauth.token.*;
import org.idp.server.token.*;
import org.idp.server.token.repository.OAuthTokenRepository;
import org.idp.server.token.validator.TokenRequestValidator;
import org.idp.server.token.verifier.TokenRequestCodeGrantVerifier;
import org.idp.server.type.oauth.*;
import org.idp.server.type.oidc.IdToken;

public class AuthorizationCodeGrantService
    implements OAuthTokenCreationService,
        AccessTokenCreatable,
        RefreshTokenCreatable,
        IdTokenCreatable {

  AuthorizationRequestRepository authorizationRequestRepository;
  OAuthTokenRepository oAuthTokenRepository;
  AuthorizationCodeGrantRepository authorizationCodeGrantRepository;
  AuthorizationGrantedRepository authorizationGrantedRepository;

  public AuthorizationCodeGrantService(
      AuthorizationRequestRepository authorizationRequestRepository,
      OAuthTokenRepository oAuthTokenRepository,
      AuthorizationCodeGrantRepository authorizationCodeGrantRepository,
      AuthorizationGrantedRepository authorizationGrantedRepository) {
    this.authorizationRequestRepository = authorizationRequestRepository;
    this.oAuthTokenRepository = oAuthTokenRepository;
    this.authorizationCodeGrantRepository = authorizationCodeGrantRepository;
    this.authorizationGrantedRepository = authorizationGrantedRepository;
  }

  @Override
  public OAuthToken create(TokenRequestContext tokenRequestContext) {
    TokenRequestValidator validator = new TokenRequestValidator(tokenRequestContext);
    validator.validate();

    AuthorizationCode code = tokenRequestContext.code();
    AuthorizationCodeGrant authorizationCodeGrant = authorizationCodeGrantRepository.find(code);
    AuthorizationRequest authorizationRequest =
        authorizationRequestRepository.find(
            authorizationCodeGrant.authorizationRequestIdentifier());

    TokenRequestCodeGrantVerifier verifier =
        new TokenRequestCodeGrantVerifier(
            tokenRequestContext, authorizationRequest, authorizationCodeGrant);
    verifier.verify();

    ServerConfiguration serverConfiguration = tokenRequestContext.serverConfiguration();
    ClientConfiguration clientConfiguration = tokenRequestContext.clientConfiguration();

    AccessToken accessToken =
        createAccessToken(
            authorizationCodeGrant.authorizationGrant(), serverConfiguration, clientConfiguration);
    RefreshToken refreshToken = createRefreshToken(serverConfiguration, clientConfiguration);
    TokenResponseBuilder tokenResponseBuilder =
        new TokenResponseBuilder()
            .add(accessToken.accessTokenValue())
            .add(TokenType.Bearer)
            .add(new ExpiresIn(serverConfiguration.accessTokenDuration()))
            .add(refreshToken.refreshTokenValue());
    if (authorizationRequest.isOidcProfile()) {
      AuthorizationCode authorizationCode = authorizationCodeGrant.authorizationCode();
      AuthorizationGrant authorizationGrant = authorizationCodeGrant.authorizationGrant();
      IdTokenCustomClaims idTokenCustomClaims =
          new IdTokenCustomClaimsBuilder()
              .add(authorizationCodeGrant.authorizationCode())
              .add(authorizationRequest.nonce())
              .add(authorizationRequest.state())
              .build();
      IdToken idToken =
          createIdToken(
              authorizationGrant.user(),
              new Authentication(),
              authorizationCodeGrant.scopes(),
              authorizationCodeGrant.idTokenClaims(),
              idTokenCustomClaims,
              serverConfiguration,
              clientConfiguration);
      tokenResponseBuilder.add(idToken);
    }
    TokenResponse tokenResponse = tokenResponseBuilder.build();
    OAuthTokenIdentifier identifier = new OAuthTokenIdentifier(UUID.randomUUID().toString());

    AuthorizationGrantedIdentifier authorizationGrantedIdentifier =
        new AuthorizationGrantedIdentifier(UUID.randomUUID().toString());
    AuthorizationGranted authorizationGranted =
        new AuthorizationGranted(
            authorizationGrantedIdentifier, authorizationCodeGrant.authorizationGrant());
    authorizationGrantedRepository.register(authorizationGranted);

    OAuthToken oAuthToken =
        new OAuthToken(
            identifier,
            tokenResponse,
            accessToken,
            refreshToken,
            authorizationCodeGrant.authorizationGrant());

    oAuthTokenRepository.register(oAuthToken);
    authorizationCodeGrantRepository.delete(authorizationCodeGrant);

    return oAuthToken;
  }
}
