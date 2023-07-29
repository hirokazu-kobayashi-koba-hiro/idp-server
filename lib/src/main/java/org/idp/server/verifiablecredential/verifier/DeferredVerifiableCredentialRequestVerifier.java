package org.idp.server.verifiablecredential.verifier;

import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.token.OAuthToken;
import org.idp.server.type.mtls.ClientCert;
import org.idp.server.verifiablecredential.VerifiableCredentialTransaction;
import org.idp.server.verifiablecredential.exception.VerifiableCredentialBadRequestException;
import org.idp.server.verifiablecredential.request.DeferredCredentialRequestParameters;
import org.idp.server.verifiablecredential.request.VerifiableCredentialRequestTransformable;

public class DeferredVerifiableCredentialRequestVerifier
    implements VerifiableCredentialRequestTransformable {

  OAuthToken oAuthToken;
  ClientCert clientCert;
  DeferredCredentialRequestParameters parameters;
  VerifiableCredentialTransaction transaction;
  ServerConfiguration serverConfiguration;

  public DeferredVerifiableCredentialRequestVerifier(
      OAuthToken oAuthToken,
      ClientCert clientCert,
      DeferredCredentialRequestParameters parameters,
      VerifiableCredentialTransaction transaction,
      ServerConfiguration serverConfiguration) {
    this.oAuthToken = oAuthToken;
    this.clientCert = clientCert;
    this.parameters = parameters;
    this.transaction = transaction;
    this.serverConfiguration = serverConfiguration;
  }

  public void verify() {
    throwExceptionIfUnSupportedVerifiableCredential();
    VerifiableCredentialOAuthTokenVerifier oAuthTokenVerifier =
        new VerifiableCredentialOAuthTokenVerifier(oAuthToken, clientCert, serverConfiguration);
    oAuthTokenVerifier.verify();
    throwExceptionIfNotContainsTransactionId();
    throwExceptionIfNotFoundTransaction();
  }

  void throwExceptionIfUnSupportedVerifiableCredential() {
    if (!serverConfiguration.hasCredentialIssuerMetadata()) {
      throw new VerifiableCredentialBadRequestException(
          "invalid_request", "unsupported verifiable credential");
    }
  }

  void throwExceptionIfNotContainsTransactionId() {
    if (!parameters.hasTransactionId()) {
      throw new VerifiableCredentialBadRequestException(
          "invalid_request",
          "transaction_id does not contains, deferred credential request must contain transaction_id");
    }
  }

  void throwExceptionIfNotFoundTransaction() {
    if (!transaction.exists()) {
      throw new VerifiableCredentialBadRequestException(
          "invalid_transaction_id",
          String.format(
              "not found verifiable credential transaction (%s)",
              parameters.transactionId().value()));
    }
  }
}
