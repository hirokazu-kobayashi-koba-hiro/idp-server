package org.idp.server.core.extension.verifiable_credentials.handler;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.basic.type.oauth.AccessTokenEntity;
import org.idp.server.basic.vc.Credential;
import org.idp.server.core.extension.verifiable_credentials.*;
import org.idp.server.core.extension.verifiable_credentials.handler.io.*;
import org.idp.server.core.extension.verifiable_credentials.repository.VerifiableCredentialTransactionRepository;
import org.idp.server.core.extension.verifiable_credentials.request.*;
import org.idp.server.core.extension.verifiable_credentials.verifier.BatchVerifiableCredentialVerifier;
import org.idp.server.core.extension.verifiable_credentials.verifier.DeferredVerifiableCredentialRequestVerifier;
import org.idp.server.core.extension.verifiable_credentials.verifier.DeferredVerifiableCredentialVerifier;
import org.idp.server.core.extension.verifiable_credentials.verifier.VerifiableCredentialVerifier;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfigurationQueryRepository;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;
import org.idp.server.core.oidc.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.core.oidc.token.AccessToken;
import org.idp.server.core.token.OAuthToken;
import org.idp.server.core.token.repository.OAuthTokenRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class CredentialHandler {

  OAuthTokenRepository oAuthTokenRepository;
  AuthorizationServerConfigurationQueryRepository authorizationServerConfigurationQueryRepository;
  ClientConfigurationQueryRepository clientConfigurationQueryRepository;
  VerifiableCredentialTransactionRepository verifiableCredentialTransactionRepository;
  VerifiableCredentialCreators creators;

  public CredentialHandler(
      OAuthTokenRepository oAuthTokenRepository,
      VerifiableCredentialTransactionRepository verifiableCredentialTransactionRepository,
      AuthorizationServerConfigurationQueryRepository
          authorizationServerConfigurationQueryRepository,
      ClientConfigurationQueryRepository clientConfigurationQueryRepository,
      VerifiableCredentialCreators creators) {
    this.oAuthTokenRepository = oAuthTokenRepository;
    this.verifiableCredentialTransactionRepository = verifiableCredentialTransactionRepository;
    this.authorizationServerConfigurationQueryRepository =
        authorizationServerConfigurationQueryRepository;
    this.clientConfigurationQueryRepository = clientConfigurationQueryRepository;
    this.creators = creators;
  }

  public CredentialResponse handleRequest(
      CredentialRequest request, VerifiableCredentialDelegate delegate) {
    AccessTokenEntity accessTokenEntity = request.toAccessToken();
    Tenant tenant = request.tenant();
    AuthorizationServerConfiguration authorizationServerConfiguration =
        authorizationServerConfigurationQueryRepository.get(tenant);
    OAuthToken oAuthToken = oAuthTokenRepository.find(tenant, accessTokenEntity);
    CredentialRequestParameters parameters = request.toParameters();

    VerifiableCredentialVerifier verifier =
        new VerifiableCredentialVerifier(
            oAuthToken, request.toClientCert(), parameters, authorizationServerConfiguration);
    verifier.verify();

    AccessToken accessToken = oAuthToken.accessToken();
    ClientConfiguration clientConfiguration =
        clientConfigurationQueryRepository.get(tenant, accessToken.clientIdentifier());
    CredentialDelegateResponse credentialDelegateResponse =
        delegate.getCredential(
            tenant,
            accessToken.subject(),
            accessToken.authorizationDetails().credentialDefinitions());
    VerifiableCredentialResponseBuilder builder =
        new VerifiableCredentialResponseBuilder().add(parameters.format());

    VerifiableCredentialTransactionCreator verifiableCredentialTransactionCreator =
        new VerifiableCredentialTransactionCreator(
            credentialDelegateResponse,
            oAuthToken,
            authorizationServerConfiguration.credentialIssuerMetadata());
    VerifiableCredentialTransaction verifiableCredentialTransaction =
        verifiableCredentialTransactionCreator.create();
    verifiableCredentialTransactionRepository.register(tenant, verifiableCredentialTransaction);

    if (credentialDelegateResponse.isIssued()) {
      VerifiableCredentialCreator verifiableCredentialCreator = creators.get(parameters.format());
      Credential credential = credentialDelegateResponse.credential();
      VerifiableCredential verifiableCredential =
          verifiableCredentialCreator.create(
              credential, authorizationServerConfiguration, clientConfiguration);
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
    AuthorizationServerConfiguration authorizationServerConfiguration =
        authorizationServerConfigurationQueryRepository.get(tenant);
    OAuthToken oAuthToken = oAuthTokenRepository.find(tenant, accessTokenEntity);
    BatchCredentialRequestParameters parameters = request.toParameters();

    BatchVerifiableCredentialVerifier verifier =
        new BatchVerifiableCredentialVerifier(
            oAuthToken, request.toClientCert(), parameters, authorizationServerConfiguration);
    verifier.verify();
    AccessToken accessToken = oAuthToken.accessToken();
    ClientConfiguration clientConfiguration =
        clientConfigurationQueryRepository.get(tenant, accessToken.clientIdentifier());
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
              authorizationServerConfiguration.credentialIssuerMetadata());
      VerifiableCredentialTransaction verifiableCredentialTransaction =
          verifiableCredentialTransactionCreator.create();
      verifiableCredentialTransactionRepository.register(tenant, verifiableCredentialTransaction);

      if (credentialDelegateResponse.isIssued()) {
        VerifiableCredentialCreator verifiableCredentialCreator =
            creators.get(batchCredentialRequest.format());
        VerifiableCredential verifiableCredential =
            verifiableCredentialCreator.create(
                credentialDelegateResponse.credential(),
                authorizationServerConfiguration,
                clientConfiguration);
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
    AuthorizationServerConfiguration authorizationServerConfiguration =
        authorizationServerConfigurationQueryRepository.get(tenant);
    OAuthToken oAuthToken = oAuthTokenRepository.find(tenant, accessTokenEntity);
    DeferredCredentialRequestParameters parameters = request.toParameters();

    VerifiableCredentialTransaction verifiableCredentialTransaction =
        verifiableCredentialTransactionRepository.find(tenant, request.transactionId());
    DeferredVerifiableCredentialRequestVerifier verifier =
        new DeferredVerifiableCredentialRequestVerifier(
            oAuthToken,
            request.toClientCert(),
            request.toParameters(),
            verifiableCredentialTransaction,
            authorizationServerConfiguration);
    verifier.verify();

    AccessToken accessToken = oAuthToken.accessToken();
    ClientConfiguration clientConfiguration =
        clientConfigurationQueryRepository.get(tenant, accessToken.clientIdentifier());
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
            credentialDelegateResponse.credential(),
            authorizationServerConfiguration,
            clientConfiguration);

    VerifiableCredentialResponse verifiableCredentialResponse =
        new VerifiableCredentialResponseBuilder()
            .add(parameters.format())
            .add(verifiableCredential)
            .build();
    return new DeferredCredentialResponse(CredentialRequestStatus.OK, verifiableCredentialResponse);
  }
}
