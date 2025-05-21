package org.idp.server.core.extension.verifiable_credentials;

import java.util.UUID;
import org.idp.server.basic.type.oauth.RequestedClientId;
import org.idp.server.basic.type.oauth.Subject;
import org.idp.server.basic.type.verifiablecredential.CredentialIssuer;
import org.idp.server.basic.type.verifiablecredential.TransactionId;
import org.idp.server.basic.vc.Credential;
import org.idp.server.core.oidc.configuration.vc.VerifiableCredentialConfiguration;
import org.idp.server.core.token.OAuthToken;

public class VerifiableCredentialTransactionCreator {

  CredentialDelegateResponse delegateResponse;
  OAuthToken oAuthToken;
  VerifiableCredentialConfiguration verifiableCredentialConfiguration;

  public VerifiableCredentialTransactionCreator(
      CredentialDelegateResponse delegateResponse,
      OAuthToken oAuthToken,
      VerifiableCredentialConfiguration verifiableCredentialConfiguration) {
    this.delegateResponse = delegateResponse;
    this.oAuthToken = oAuthToken;
    this.verifiableCredentialConfiguration = verifiableCredentialConfiguration;
  }

  public VerifiableCredentialTransaction create() {

    TransactionId transactionId = new TransactionId(UUID.randomUUID().toString());
    CredentialIssuer credentialIssuer = verifiableCredentialConfiguration.credentialIssuer();
    RequestedClientId requestedClientId = oAuthToken.accessToken().requestedClientId();
    Subject subject = oAuthToken.subject();
    Credential credential = delegateResponse.credential();
    VerifiableCredentialTransactionStatus status = delegateResponse.status();

    return new VerifiableCredentialTransaction(
        transactionId, credentialIssuer, requestedClientId, subject, credential, status);
  }
}
