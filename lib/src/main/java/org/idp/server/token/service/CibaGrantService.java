package org.idp.server.token.service;

import java.util.UUID;
import org.idp.server.ciba.grant.CibaGrant;
import org.idp.server.ciba.repository.BackchannelAuthenticationRequestRepository;
import org.idp.server.ciba.repository.CibaGrantRepository;
import org.idp.server.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.grantmangment.AuthorizationGranted;
import org.idp.server.grantmangment.AuthorizationGrantedIdentifier;
import org.idp.server.grantmangment.AuthorizationGrantedRepository;
import org.idp.server.oauth.authentication.Authentication;
import org.idp.server.oauth.identity.*;
import org.idp.server.oauth.token.*;
import org.idp.server.token.*;
import org.idp.server.token.repository.OAuthTokenRepository;
import org.idp.server.token.validator.CibaGrantValidator;
import org.idp.server.token.verifier.CibaGrantVerifier;
import org.idp.server.type.ciba.AuthReqId;
import org.idp.server.type.extension.GrantFlow;
import org.idp.server.type.oauth.ExpiresIn;
import org.idp.server.type.oauth.TokenType;
import org.idp.server.type.oidc.IdToken;

public class CibaGrantService
    implements OAuthTokenCreationService,
        AccessTokenCreatable,
        RefreshTokenCreatable,
        IdTokenCreatable {

  BackchannelAuthenticationRequestRepository backchannelAuthenticationRequestRepository;
  CibaGrantRepository cibaGrantRepository;
  OAuthTokenRepository oAuthTokenRepository;
  AuthorizationGrantedRepository authorizationGrantedRepository;

  public CibaGrantService(
      BackchannelAuthenticationRequestRepository backchannelAuthenticationRequestRepository,
      CibaGrantRepository cibaGrantRepository,
      OAuthTokenRepository oAuthTokenRepository,
      AuthorizationGrantedRepository authorizationGrantedRepository) {
    this.backchannelAuthenticationRequestRepository = backchannelAuthenticationRequestRepository;
    this.cibaGrantRepository = cibaGrantRepository;
    this.oAuthTokenRepository = oAuthTokenRepository;
    this.authorizationGrantedRepository = authorizationGrantedRepository;
  }

  @Override
  public OAuthToken create(TokenRequestContext tokenRequestContext) {
    CibaGrantValidator validator = new CibaGrantValidator(tokenRequestContext);
    validator.validate();

    AuthReqId authReqId = tokenRequestContext.authReqId();
    CibaGrant cibaGrant = cibaGrantRepository.find(authReqId);
    BackchannelAuthenticationRequest backchannelAuthenticationRequest =
        backchannelAuthenticationRequestRepository.find(
            cibaGrant.backchannelAuthenticationRequestIdentifier());

    CibaGrantVerifier verifier =
        new CibaGrantVerifier(tokenRequestContext, backchannelAuthenticationRequest, cibaGrant);
    verifier.verify();

    ServerConfiguration serverConfiguration = tokenRequestContext.serverConfiguration();
    ClientConfiguration clientConfiguration = tokenRequestContext.clientConfiguration();

    AccessToken accessToken =
        createAccessToken(cibaGrant.authorizationGrant(), serverConfiguration, clientConfiguration);
    RefreshToken refreshToken = createRefreshToken(serverConfiguration, clientConfiguration);
    TokenResponseBuilder tokenResponseBuilder =
        new TokenResponseBuilder()
            .add(accessToken.accessTokenValue())
            .add(TokenType.Bearer)
            .add(new ExpiresIn(serverConfiguration.accessTokenDuration()))
            .add(refreshToken.refreshTokenValue());
    IdTokenCustomClaims idTokenCustomClaims = new IdTokenCustomClaimsBuilder().build();
    IdToken idToken =
        createIdToken(
            cibaGrant.user(),
            new Authentication(),
            GrantFlow.ciba,
            cibaGrant.scopes(),
            new IdTokenClaims(),
            idTokenCustomClaims,
            serverConfiguration,
            clientConfiguration);
    tokenResponseBuilder.add(idToken);

    TokenResponse tokenResponse = tokenResponseBuilder.build();
    OAuthTokenIdentifier identifier = new OAuthTokenIdentifier(UUID.randomUUID().toString());

    AuthorizationGrantedIdentifier authorizationGrantedIdentifier =
        new AuthorizationGrantedIdentifier(UUID.randomUUID().toString());
    AuthorizationGranted authorizationGranted =
        new AuthorizationGranted(authorizationGrantedIdentifier, cibaGrant.authorizationGrant());
    authorizationGrantedRepository.register(authorizationGranted);

    OAuthToken oAuthToken =
        new OAuthToken(
            identifier, tokenResponse, accessToken, refreshToken, cibaGrant.authorizationGrant());

    oAuthTokenRepository.register(oAuthToken);
    cibaGrantRepository.delete(cibaGrant);

    return oAuthToken;
  }
}
