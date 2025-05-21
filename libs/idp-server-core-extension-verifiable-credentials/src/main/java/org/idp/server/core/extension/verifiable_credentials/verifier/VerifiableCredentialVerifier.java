package org.idp.server.core.extension.verifiable_credentials.verifier;

import org.idp.server.basic.type.mtls.ClientCert;
import org.idp.server.core.extension.verifiable_credentials.exception.VerifiableCredentialBadRequestException;
import org.idp.server.core.extension.verifiable_credentials.exception.VerifiableCredentialRequestInvalidException;
import org.idp.server.core.extension.verifiable_credentials.request.CredentialRequestParameters;
import org.idp.server.core.extension.verifiable_credentials.request.VerifiableCredentialRequest;
import org.idp.server.core.extension.verifiable_credentials.request.VerifiableCredentialRequestTransformable;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.token.OAuthToken;

public class VerifiableCredentialVerifier implements VerifiableCredentialRequestTransformable {

  OAuthToken oAuthToken;
  ClientCert clientCert;
  CredentialRequestParameters parameters;
  AuthorizationServerConfiguration authorizationServerConfiguration;

  public VerifiableCredentialVerifier(
      OAuthToken oAuthToken,
      ClientCert clientCert,
      CredentialRequestParameters parameters,
      AuthorizationServerConfiguration authorizationServerConfiguration) {
    this.oAuthToken = oAuthToken;
    this.clientCert = clientCert;
    this.parameters = parameters;
    this.authorizationServerConfiguration = authorizationServerConfiguration;
  }

  public void verify() {
    throwExceptionIfUnSupportedVerifiableCredential();
    VerifiableCredentialOAuthTokenVerifier oAuthTokenVerifier =
        new VerifiableCredentialOAuthTokenVerifier(
            oAuthToken, clientCert, authorizationServerConfiguration);
    oAuthTokenVerifier.verify();
    VerifiableCredentialRequest request = transformAndVerify();
    VerifiableCredentialRequestVerifier requestVerifier =
        new VerifiableCredentialRequestVerifier(request, authorizationServerConfiguration);
    requestVerifier.verify();
  }

  void throwExceptionIfUnSupportedVerifiableCredential() {
    if (!authorizationServerConfiguration.hasCredentialIssuerMetadata()) {
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
