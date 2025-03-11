package org.idp.server.core.verifiablecredential.verifier;

import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.token.OAuthToken;
import org.idp.server.core.type.mtls.ClientCert;
import org.idp.server.core.verifiablecredential.exception.VerifiableCredentialBadRequestException;
import org.idp.server.core.verifiablecredential.exception.VerifiableCredentialRequestInvalidException;
import org.idp.server.core.verifiablecredential.request.CredentialRequestParameters;
import org.idp.server.core.verifiablecredential.request.VerifiableCredentialRequest;
import org.idp.server.core.verifiablecredential.request.VerifiableCredentialRequestTransformable;

public class VerifiableCredentialVerifier implements VerifiableCredentialRequestTransformable {

  OAuthToken oAuthToken;
  ClientCert clientCert;
  CredentialRequestParameters parameters;
  ServerConfiguration serverConfiguration;

  public VerifiableCredentialVerifier(
      OAuthToken oAuthToken,
      ClientCert clientCert,
      CredentialRequestParameters parameters,
      ServerConfiguration serverConfiguration) {
    this.oAuthToken = oAuthToken;
    this.clientCert = clientCert;
    this.parameters = parameters;
    this.serverConfiguration = serverConfiguration;
  }

  public void verify() {
    throwExceptionIfUnSupportedVerifiableCredential();
    VerifiableCredentialOAuthTokenVerifier oAuthTokenVerifier =
        new VerifiableCredentialOAuthTokenVerifier(oAuthToken, clientCert, serverConfiguration);
    oAuthTokenVerifier.verify();
    VerifiableCredentialRequest request = transformAndVerify();
    VerifiableCredentialRequestVerifier requestVerifier =
        new VerifiableCredentialRequestVerifier(request, serverConfiguration);
    requestVerifier.verify();
  }

  void throwExceptionIfUnSupportedVerifiableCredential() {
    if (!serverConfiguration.hasCredentialIssuerMetadata()) {
      throw new VerifiableCredentialBadRequestException(
          "invalid_request", "unsupported verifiable credential");
    }
  }

  VerifiableCredentialRequest transformAndVerify() {
    try {
      return transformRequest(parameters.values());
    } catch (VerifiableCredentialRequestInvalidException exception) {
      throw new VerifiableCredentialBadRequestException("invalid_request", exception.getMessage());
    }
  }
}
