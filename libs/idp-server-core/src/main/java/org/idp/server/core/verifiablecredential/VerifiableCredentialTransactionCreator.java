package org.idp.server.core.verifiablecredential;

import java.util.UUID;
import org.idp.server.core.basic.vc.Credential;
import org.idp.server.core.configuration.VerifiableCredentialConfiguration;
import org.idp.server.core.token.OAuthToken;
import org.idp.server.core.type.oauth.RequestedClientId;
import org.idp.server.core.type.oauth.Subject;
import org.idp.server.core.type.verifiablecredential.CredentialIssuer;
import org.idp.server.core.type.verifiablecredential.TransactionId;

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
    RequestedClientId requestedClientId = oAuthToken.accessToken().clientId();
    Subject subject = oAuthToken.subject();
    Credential credential = delegateResponse.credential();
    VerifiableCredentialTransactionStatus status = delegateResponse.status();

    return new VerifiableCredentialTransaction(
        transactionId, credentialIssuer, requestedClientId, subject, credential, status);
  }
}
