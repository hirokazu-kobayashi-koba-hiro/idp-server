package org.idp.server.handler.credential;

import org.idp.server.configuration.ClientConfigurationRepository;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.configuration.ServerConfigurationRepository;
import org.idp.server.handler.credential.io.CredentialRequest;
import org.idp.server.handler.credential.io.CredentialRequestStatus;
import org.idp.server.handler.credential.io.CredentialResponse;
import org.idp.server.token.OAuthToken;
import org.idp.server.token.repository.OAuthTokenRepository;
import org.idp.server.type.oauth.AccessTokenValue;
import org.idp.server.type.oauth.TokenIssuer;
import org.idp.server.verifiablecredential.VerifiableCredentialResponse;
import org.idp.server.verifiablecredential.verifier.VerifiableCredentialVerifier;

public class CredentialHandler {

  OAuthTokenRepository oAuthTokenRepository;
  ServerConfigurationRepository serverConfigurationRepository;
  ClientConfigurationRepository clientConfigurationRepository;

  public CredentialHandler(
      OAuthTokenRepository oAuthTokenRepository,
      ServerConfigurationRepository serverConfigurationRepository,
      ClientConfigurationRepository clientConfigurationRepository) {
    this.oAuthTokenRepository = oAuthTokenRepository;
    this.serverConfigurationRepository = serverConfigurationRepository;
    this.clientConfigurationRepository = clientConfigurationRepository;
  }

  public CredentialResponse handle(CredentialRequest request) {
    AccessTokenValue accessTokenValue = request.toAccessToken();
    TokenIssuer tokenIssuer = request.toTokenIssuer();
    ServerConfiguration serverConfiguration = serverConfigurationRepository.get(tokenIssuer);
    OAuthToken oAuthToken = oAuthTokenRepository.find(tokenIssuer, accessTokenValue);

    VerifiableCredentialVerifier verifier =
        new VerifiableCredentialVerifier(
            oAuthToken, request.toClientCert(), request.toParameters());
    verifier.verify();
    VerifiableCredentialResponse verifiableCredentialResponse = new VerifiableCredentialResponse();
    return new CredentialResponse(CredentialRequestStatus.OK, verifiableCredentialResponse);
  }
}
