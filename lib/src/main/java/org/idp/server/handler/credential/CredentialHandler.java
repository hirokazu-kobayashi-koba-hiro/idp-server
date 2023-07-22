package org.idp.server.handler.credential;

import org.idp.server.basic.vc.VerifiableCredential;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ClientConfigurationRepository;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.configuration.ServerConfigurationRepository;
import org.idp.server.handler.credential.io.CredentialRequest;
import org.idp.server.handler.credential.io.CredentialRequestStatus;
import org.idp.server.handler.credential.io.CredentialResponse;
import org.idp.server.oauth.token.AccessToken;
import org.idp.server.token.OAuthToken;
import org.idp.server.token.repository.OAuthTokenRepository;
import org.idp.server.type.oauth.AccessTokenValue;
import org.idp.server.type.oauth.TokenIssuer;
import org.idp.server.verifiablecredential.*;
import org.idp.server.verifiablecredential.VerifiableCredentialCreators;
import org.idp.server.verifiablecredential.VerifiableCredentialJwt;
import org.idp.server.verifiablecredential.verifier.VerifiableCredentialVerifier;

public class CredentialHandler {

  OAuthTokenRepository oAuthTokenRepository;
  ServerConfigurationRepository serverConfigurationRepository;
  ClientConfigurationRepository clientConfigurationRepository;
  VerifiableCredentialCreators creators;

  public CredentialHandler(
      OAuthTokenRepository oAuthTokenRepository,
      ServerConfigurationRepository serverConfigurationRepository,
      ClientConfigurationRepository clientConfigurationRepository) {
    this.oAuthTokenRepository = oAuthTokenRepository;
    this.serverConfigurationRepository = serverConfigurationRepository;
    this.clientConfigurationRepository = clientConfigurationRepository;
    this.creators = new VerifiableCredentialCreators();
  }

  public CredentialResponse handle(
      CredentialRequest request, VerifiableCredentialDelegate delegate) {
    AccessTokenValue accessTokenValue = request.toAccessToken();
    TokenIssuer tokenIssuer = request.toTokenIssuer();
    ServerConfiguration serverConfiguration = serverConfigurationRepository.get(tokenIssuer);
    OAuthToken oAuthToken = oAuthTokenRepository.find(tokenIssuer, accessTokenValue);
    CredentialRequestParameters parameters = request.toParameters();

    VerifiableCredentialVerifier verifier =
        new VerifiableCredentialVerifier(
            oAuthToken, request.toClientCert(), parameters, serverConfiguration);
    verifier.verify();
    AccessToken accessToken = oAuthToken.accessToken();
    ClientConfiguration clientConfiguration =
        clientConfigurationRepository.get(tokenIssuer, accessToken.clientId());
    VerifiableCredential verifiableCredential =
        delegate.getCredential(
            tokenIssuer,
            accessToken.subject(),
            accessToken.authorizationDetails().credentialDefinitions());
    VerifiableCredentialCreator verifiableCredentialCreator = creators.get(parameters.format());
    VerifiableCredentialJwt verifiableCredentialJwt =
        verifiableCredentialCreator.create(
            verifiableCredential, serverConfiguration, clientConfiguration);
    VerifiableCredentialResponse verifiableCredentialResponse =
        new VerifiableCredentialResponseBuilder()
            .add(parameters.format())
            .add(verifiableCredentialJwt)
            .build();
    return new CredentialResponse(CredentialRequestStatus.OK, verifiableCredentialResponse);
  }
}
