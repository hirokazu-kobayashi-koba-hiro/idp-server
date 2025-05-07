package org.idp.server.core.token.service;

import java.util.UUID;
import org.idp.server.basic.type.ciba.AuthReqId;
import org.idp.server.basic.type.oidc.IdToken;
import org.idp.server.core.ciba.grant.CibaGrant;
import org.idp.server.core.ciba.repository.BackchannelAuthenticationRequestRepository;
import org.idp.server.core.ciba.repository.CibaGrantRepository;
import org.idp.server.core.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.core.grant_management.AuthorizationGranted;
import org.idp.server.core.grant_management.AuthorizationGrantedIdentifier;
import org.idp.server.core.grant_management.AuthorizationGrantedRepository;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.oidc.authentication.Authentication;
import org.idp.server.core.oidc.clientcredentials.ClientCredentials;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;
import org.idp.server.core.oidc.grant.AuthorizationGrant;
import org.idp.server.core.oidc.identity.IdTokenCreatable;
import org.idp.server.core.oidc.identity.IdTokenCustomClaims;
import org.idp.server.core.oidc.identity.IdTokenCustomClaimsBuilder;
import org.idp.server.core.oidc.identity.RequestedClaimsPayload;
import org.idp.server.core.oidc.token.*;
import org.idp.server.core.token.*;
import org.idp.server.core.token.repository.OAuthTokenRepository;
import org.idp.server.core.token.validator.CibaGrantValidator;
import org.idp.server.core.token.verifier.CibaGrantVerifier;

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

    Tenant tenant = tokenRequestContext.tenant();
    AuthReqId authReqId = tokenRequestContext.authReqId();
    CibaGrant cibaGrant = cibaGrantRepository.find(tenant, authReqId);
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
        createAccessToken(
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
        createIdToken(
            cibaGrant.user(),
            new Authentication(),
            authorizationGrant,
            idTokenCustomClaims,
            new RequestedClaimsPayload(),
            authorizationServerConfiguration,
            clientConfiguration);
    oAuthTokenBuilder.add(idToken);

    registerOrUpdate(tenant, cibaGrant);

    OAuthToken oAuthToken = oAuthTokenBuilder.build();

    oAuthTokenRepository.register(tenant, oAuthToken);
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
