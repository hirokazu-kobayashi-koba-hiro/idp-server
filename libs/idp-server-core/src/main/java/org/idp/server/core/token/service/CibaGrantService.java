package org.idp.server.core.token.service;

import java.util.UUID;
import org.idp.server.core.ciba.grant.CibaGrant;
import org.idp.server.core.ciba.repository.BackchannelAuthenticationRequestRepository;
import org.idp.server.core.ciba.repository.CibaGrantRepository;
import org.idp.server.core.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.grantmangment.AuthorizationGranted;
import org.idp.server.core.grantmangment.AuthorizationGrantedIdentifier;
import org.idp.server.core.grantmangment.AuthorizationGrantedRepository;
import org.idp.server.core.oauth.authentication.Authentication;
import org.idp.server.core.oauth.clientcredentials.ClientCredentials;
import org.idp.server.core.oauth.grant.AuthorizationGrant;
import org.idp.server.core.oauth.identity.*;
import org.idp.server.core.oauth.token.*;
import org.idp.server.core.token.*;
import org.idp.server.core.token.repository.OAuthTokenRepository;
import org.idp.server.core.token.validator.CibaGrantValidator;
import org.idp.server.core.token.verifier.CibaGrantVerifier;
import org.idp.server.core.type.ciba.AuthReqId;
import org.idp.server.core.type.extension.GrantFlow;
import org.idp.server.core.type.oidc.IdToken;

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
  public OAuthToken create(
      TokenRequestContext tokenRequestContext, ClientCredentials clientCredentials) {
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

    AuthorizationGrant authorizationGrant = cibaGrant.authorizationGrant();
    AccessToken accessToken =
        createAccessToken(
            authorizationGrant, serverConfiguration, clientConfiguration, clientCredentials);
    RefreshToken refreshToken = createRefreshToken(serverConfiguration, clientConfiguration);
    OAuthTokenBuilder oAuthTokenBuilder =
        new OAuthTokenBuilder(new OAuthTokenIdentifier(UUID.randomUUID().toString()))
            .add(accessToken)
            .add(refreshToken);
    IdTokenCustomClaims idTokenCustomClaims = new IdTokenCustomClaimsBuilder().build();
    IdToken idToken =
        createIdToken(
            cibaGrant.user(),
            new Authentication(),
            GrantFlow.ciba,
            authorizationGrant,
            new IdTokenClaims(),
            idTokenCustomClaims,
            serverConfiguration,
            clientConfiguration);
    oAuthTokenBuilder.add(idToken);

    registerOrUpdate(cibaGrant);

    OAuthToken oAuthToken = oAuthTokenBuilder.build();

    oAuthTokenRepository.register(oAuthToken);
    cibaGrantRepository.delete(cibaGrant);

    return oAuthToken;
  }

  private void registerOrUpdate(CibaGrant cibaGrant) {
    AuthorizationGranted latest =
        authorizationGrantedRepository.find(
            cibaGrant.tenantIdentifier(), cibaGrant.requestedClientId(), cibaGrant.user());

    if (latest.exists()) {
      AuthorizationGranted merge = latest.merge(cibaGrant.authorizationGrant());

      authorizationGrantedRepository.update(merge);
      return;
    }
    AuthorizationGrantedIdentifier authorizationGrantedIdentifier =
        new AuthorizationGrantedIdentifier(UUID.randomUUID().toString());
    AuthorizationGranted authorizationGranted =
        new AuthorizationGranted(authorizationGrantedIdentifier, cibaGrant.authorizationGrant());
    authorizationGrantedRepository.register(authorizationGranted);
  }
}
