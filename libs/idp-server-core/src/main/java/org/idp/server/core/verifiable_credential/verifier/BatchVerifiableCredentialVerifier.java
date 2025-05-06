package org.idp.server.core.verifiable_credential.verifier;

import org.idp.server.basic.type.mtls.ClientCert;
import org.idp.server.core.oidc.configuration.ServerConfiguration;
import org.idp.server.core.token.OAuthToken;
import org.idp.server.core.verifiable_credential.exception.VerifiableCredentialBadRequestException;
import org.idp.server.core.verifiable_credential.exception.VerifiableCredentialRequestInvalidException;
import org.idp.server.core.verifiable_credential.request.BatchCredentialRequestParameters;
import org.idp.server.core.verifiable_credential.request.BatchCredentialRequests;
import org.idp.server.core.verifiable_credential.request.VerifiableCredentialRequestTransformable;

public class BatchVerifiableCredentialVerifier implements VerifiableCredentialRequestTransformable {

  OAuthToken oAuthToken;
  ClientCert clientCert;
  BatchCredentialRequestParameters parameters;
  ServerConfiguration serverConfiguration;

  public BatchVerifiableCredentialVerifier(OAuthToken oAuthToken, ClientCert clientCert, BatchCredentialRequestParameters parameters, ServerConfiguration serverConfiguration) {
    this.oAuthToken = oAuthToken;
    this.clientCert = clientCert;
    this.parameters = parameters;
    this.serverConfiguration = serverConfiguration;
  }

  public void verify() {
    throwExceptionIfUnSupportedVerifiableCredential();
    VerifiableCredentialOAuthTokenVerifier oAuthTokenVerifier = new VerifiableCredentialOAuthTokenVerifier(oAuthToken, clientCert, serverConfiguration);
    oAuthTokenVerifier.verify();
    BatchCredentialRequests verifiableCredentialRequests = transformAndVerify();
    verifiableCredentialRequests.forEach(request -> {
      VerifiableCredentialRequestVerifier requestVerifier = new VerifiableCredentialRequestVerifier(request, serverConfiguration);
      requestVerifier.verify();
    });
  }

  void throwExceptionIfUnSupportedVerifiableCredential() {
    if (!serverConfiguration.hasCredentialIssuerMetadata()) {
      throw new VerifiableCredentialBadRequestException("invalid_request", "unsupported verifiable credential");
    }
  }

  BatchCredentialRequests transformAndVerify() {
    try {
      return transformBatchRequest(parameters.credentialRequests());
    } catch (VerifiableCredentialRequestInvalidException exception) {
      throw new VerifiableCredentialBadRequestException("invalid_request", exception.getMessage());
    }
  }
}
