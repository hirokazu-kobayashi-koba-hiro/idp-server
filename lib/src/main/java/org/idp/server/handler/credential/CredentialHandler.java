package org.idp.server.handler.credential;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.basic.vc.VerifiableCredential;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ClientConfigurationRepository;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.configuration.ServerConfigurationRepository;
import org.idp.server.handler.credential.io.*;
import org.idp.server.oauth.token.AccessToken;
import org.idp.server.token.OAuthToken;
import org.idp.server.token.repository.OAuthTokenRepository;
import org.idp.server.type.oauth.AccessTokenEntity;
import org.idp.server.type.oauth.TokenIssuer;
import org.idp.server.verifiablecredential.*;
import org.idp.server.verifiablecredential.VerifiableCredentialCreators;
import org.idp.server.verifiablecredential.VerifiableCredentialJwt;
import org.idp.server.verifiablecredential.request.BatchCredentialRequestParameters;
import org.idp.server.verifiablecredential.request.BatchCredentialRequests;
import org.idp.server.verifiablecredential.request.CredentialRequestParameters;
import org.idp.server.verifiablecredential.verifier.BatchVerifiableCredentialVerifier;
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

  public CredentialResponse handleRequest(
      CredentialRequest request, VerifiableCredentialDelegate delegate) {
    AccessTokenEntity accessTokenEntity = request.toAccessToken();
    TokenIssuer tokenIssuer = request.toTokenIssuer();
    ServerConfiguration serverConfiguration = serverConfigurationRepository.get(tokenIssuer);
    OAuthToken oAuthToken = oAuthTokenRepository.find(tokenIssuer, accessTokenEntity);
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

  public BatchCredentialResponse handleBatchRequest(
      BatchCredentialRequest request, VerifiableCredentialDelegate delegate) {
    AccessTokenEntity accessTokenEntity = request.toAccessToken();
    TokenIssuer tokenIssuer = request.toTokenIssuer();
    ServerConfiguration serverConfiguration = serverConfigurationRepository.get(tokenIssuer);
    OAuthToken oAuthToken = oAuthTokenRepository.find(tokenIssuer, accessTokenEntity);
    BatchCredentialRequestParameters parameters = request.toParameters();

    BatchVerifiableCredentialVerifier verifier =
        new BatchVerifiableCredentialVerifier(
            oAuthToken, request.toClientCert(), parameters, serverConfiguration);
    verifier.verify();
    AccessToken accessToken = oAuthToken.accessToken();
    ClientConfiguration clientConfiguration =
        clientConfigurationRepository.get(tokenIssuer, accessToken.clientId());
    BatchCredentialRequests batchCredentialRequests = parameters.toBatchCredentialRequest();
    VerifiableCredential verifiableCredential =
        delegate.getCredential(
            tokenIssuer,
            accessToken.subject(),
            accessToken.authorizationDetails().credentialDefinitions());
    List<BatchVerifiableCredentialResponse> responsesList = new ArrayList<>();
    batchCredentialRequests.forEach(
        batchCredentialRequest -> {
          VerifiableCredentialCreator verifiableCredentialCreator =
              creators.get(batchCredentialRequest.format());
          VerifiableCredentialJwt verifiableCredentialJwt =
              verifiableCredentialCreator.create(
                  verifiableCredential, serverConfiguration, clientConfiguration);
          responsesList.add(
              new BatchVerifiableCredentialResponse(
                  batchCredentialRequest.format(), verifiableCredentialJwt));
        });

    BatchVerifiableCredentialResponses responses =
        new BatchVerifiableCredentialResponsesBuilder().add(responsesList).build();

    return new BatchCredentialResponse(CredentialRequestStatus.OK, responses);
  }
}
