package org.idp.server.core.handler.credential;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.core.basic.vc.Credential;
import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ClientConfigurationRepository;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.configuration.ServerConfigurationRepository;
import org.idp.server.core.handler.credential.io.*;
import org.idp.server.core.oauth.token.AccessToken;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.token.OAuthToken;
import org.idp.server.core.token.repository.OAuthTokenRepository;
import org.idp.server.core.type.oauth.AccessTokenEntity;
import org.idp.server.core.verifiablecredential.*;
import org.idp.server.core.verifiablecredential.VerifiableCredential;
import org.idp.server.core.verifiablecredential.VerifiableCredentialCreators;
import org.idp.server.core.verifiablecredential.repository.VerifiableCredentialTransactionRepository;
import org.idp.server.core.verifiablecredential.request.*;
import org.idp.server.core.verifiablecredential.verifier.BatchVerifiableCredentialVerifier;
import org.idp.server.core.verifiablecredential.verifier.DeferredVerifiableCredentialRequestVerifier;
import org.idp.server.core.verifiablecredential.verifier.DeferredVerifiableCredentialVerifier;
import org.idp.server.core.verifiablecredential.verifier.VerifiableCredentialVerifier;

public class CredentialHandler {

  OAuthTokenRepository oAuthTokenRepository;
  ServerConfigurationRepository serverConfigurationRepository;
  ClientConfigurationRepository clientConfigurationRepository;
  VerifiableCredentialTransactionRepository verifiableCredentialTransactionRepository;
  VerifiableCredentialCreators creators;

  public CredentialHandler(
      OAuthTokenRepository oAuthTokenRepository,
      VerifiableCredentialTransactionRepository verifiableCredentialTransactionRepository,
      ServerConfigurationRepository serverConfigurationRepository,
      ClientConfigurationRepository clientConfigurationRepository,
      VerifiableCredentialCreators creators) {
    this.oAuthTokenRepository = oAuthTokenRepository;
    this.verifiableCredentialTransactionRepository = verifiableCredentialTransactionRepository;
    this.serverConfigurationRepository = serverConfigurationRepository;
    this.clientConfigurationRepository = clientConfigurationRepository;
    this.creators = creators;
  }

  public CredentialResponse handleRequest(
      CredentialRequest request, VerifiableCredentialDelegate delegate) {
    AccessTokenEntity accessTokenEntity = request.toAccessToken();
    Tenant tenant = request.tenant();
    ServerConfiguration serverConfiguration =
        serverConfigurationRepository.get(tenant.identifier());
    OAuthToken oAuthToken = oAuthTokenRepository.find(tenant, accessTokenEntity);
    CredentialRequestParameters parameters = request.toParameters();

    VerifiableCredentialVerifier verifier =
        new VerifiableCredentialVerifier(
            oAuthToken, request.toClientCert(), parameters, serverConfiguration);
    verifier.verify();
    
    AccessToken accessToken = oAuthToken.accessToken();
    ClientConfiguration clientConfiguration =
        clientConfigurationRepository.get(tenant, accessToken.clientIdentifier());
    CredentialDelegateResponse credentialDelegateResponse =
        delegate.getCredential(
            tenant,
            accessToken.subject(),
            accessToken.authorizationDetails().credentialDefinitions());
    VerifiableCredentialResponseBuilder builder =
        new VerifiableCredentialResponseBuilder().add(parameters.format());

    VerifiableCredentialTransactionCreator verifiableCredentialTransactionCreator =
        new VerifiableCredentialTransactionCreator(
            credentialDelegateResponse, oAuthToken, serverConfiguration.credentialIssuerMetadata());
    VerifiableCredentialTransaction verifiableCredentialTransaction =
        verifiableCredentialTransactionCreator.create();
    verifiableCredentialTransactionRepository.register(verifiableCredentialTransaction);

    if (credentialDelegateResponse.isIssued()) {
      VerifiableCredentialCreator verifiableCredentialCreator = creators.get(parameters.format());
      Credential credential = credentialDelegateResponse.credential();
      VerifiableCredential verifiableCredential =
          verifiableCredentialCreator.create(credential, serverConfiguration, clientConfiguration);
      builder.add(verifiableCredential);
    }
    if (credentialDelegateResponse.isPending()) {
      builder.add(verifiableCredentialTransaction.transactionId());
    }

    VerifiableCredentialResponse verifiableCredentialResponse = builder.build();
    return new CredentialResponse(CredentialRequestStatus.OK, verifiableCredentialResponse);
  }

  public BatchCredentialResponse handleBatchRequest(
      BatchCredentialRequest request, VerifiableCredentialDelegate delegate) {
    AccessTokenEntity accessTokenEntity = request.toAccessToken();
    Tenant tenant = request.tenant();
    ServerConfiguration serverConfiguration =
        serverConfigurationRepository.get(tenant.identifier());
    OAuthToken oAuthToken = oAuthTokenRepository.find(tenant, accessTokenEntity);
    BatchCredentialRequestParameters parameters = request.toParameters();

    BatchVerifiableCredentialVerifier verifier =
        new BatchVerifiableCredentialVerifier(
            oAuthToken, request.toClientCert(), parameters, serverConfiguration);
    verifier.verify();
    AccessToken accessToken = oAuthToken.accessToken();
    ClientConfiguration clientConfiguration =
        clientConfigurationRepository.get(tenant, accessToken.clientIdentifier());
    BatchCredentialRequests batchCredentialRequests = parameters.toBatchCredentialRequest();

    // FIXME
    BatchVerifiableCredentialResponsesBuilder batchVerifiableCredentialResponsesBuilder =
        new BatchVerifiableCredentialResponsesBuilder();
    List<BatchVerifiableCredentialResponse> responsesList = new ArrayList<>();
    for (VerifiableCredentialRequest batchCredentialRequest : batchCredentialRequests) {
      CredentialDelegateResponse credentialDelegateResponse =
          delegate.getCredential(
              tenant,
              accessToken.subject(),
              accessToken.authorizationDetails().credentialDefinitions());

      VerifiableCredentialTransactionCreator verifiableCredentialTransactionCreator =
          new VerifiableCredentialTransactionCreator(
              credentialDelegateResponse,
              oAuthToken,
              serverConfiguration.credentialIssuerMetadata());
      VerifiableCredentialTransaction verifiableCredentialTransaction =
          verifiableCredentialTransactionCreator.create();
      verifiableCredentialTransactionRepository.register(verifiableCredentialTransaction);

      if (credentialDelegateResponse.isIssued()) {
        VerifiableCredentialCreator verifiableCredentialCreator =
            creators.get(batchCredentialRequest.format());
        VerifiableCredential verifiableCredential =
            verifiableCredentialCreator.create(
                credentialDelegateResponse.credential(), serverConfiguration, clientConfiguration);
        responsesList.add(
            new BatchVerifiableCredentialResponse(
                batchCredentialRequest.format(), verifiableCredential));
      }
      if (credentialDelegateResponse.isPending()) {
        responsesList.add(
            new BatchVerifiableCredentialResponse(verifiableCredentialTransaction.transactionId()));
      }
    }

    batchVerifiableCredentialResponsesBuilder.add(responsesList);

    BatchVerifiableCredentialResponses responses =
        batchVerifiableCredentialResponsesBuilder.build();

    return new BatchCredentialResponse(CredentialRequestStatus.OK, responses);
  }

  public DeferredCredentialResponse handleDeferredRequest(
      DeferredCredentialRequest request, VerifiableCredentialDelegate delegate) {
    AccessTokenEntity accessTokenEntity = request.toAccessToken();
    Tenant tenant = request.tenant();
    ServerConfiguration serverConfiguration =
        serverConfigurationRepository.get(tenant.identifier());
    OAuthToken oAuthToken = oAuthTokenRepository.find(tenant, accessTokenEntity);
    DeferredCredentialRequestParameters parameters = request.toParameters();

    VerifiableCredentialTransaction verifiableCredentialTransaction =
        verifiableCredentialTransactionRepository.find(request.transactionId());
    DeferredVerifiableCredentialRequestVerifier verifier =
        new DeferredVerifiableCredentialRequestVerifier(
            oAuthToken,
            request.toClientCert(),
            request.toParameters(),
            verifiableCredentialTransaction,
            serverConfiguration);
    verifier.verify();

    AccessToken accessToken = oAuthToken.accessToken();
    ClientConfiguration clientConfiguration =
        clientConfigurationRepository.get(tenant, accessToken.clientIdentifier());
    CredentialDelegateResponse credentialDelegateResponse =
        delegate.getCredential(
            tenant,
            accessToken.subject(),
            accessToken.authorizationDetails().credentialDefinitions());

    DeferredVerifiableCredentialVerifier verifiableCredentialVerifier =
        new DeferredVerifiableCredentialVerifier(credentialDelegateResponse);
    verifiableCredentialVerifier.verify();

    VerifiableCredentialCreator verifiableCredentialCreator = creators.get(parameters.format());
    VerifiableCredential verifiableCredential =
        verifiableCredentialCreator.create(
            credentialDelegateResponse.credential(), serverConfiguration, clientConfiguration);

    VerifiableCredentialResponse verifiableCredentialResponse =
        new VerifiableCredentialResponseBuilder()
            .add(parameters.format())
            .add(verifiableCredential)
            .build();
    return new DeferredCredentialResponse(CredentialRequestStatus.OK, verifiableCredentialResponse);
  }
}
