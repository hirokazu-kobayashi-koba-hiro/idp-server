package org.idp.server.verifiablecredential;

import java.util.UUID;
import org.idp.server.basic.vc.VerifiableCredential;
import org.idp.server.configuration.VerifiableCredentialConfiguration;
import org.idp.server.token.OAuthToken;
import org.idp.server.type.oauth.ClientId;
import org.idp.server.type.oauth.Subject;
import org.idp.server.type.verifiablecredential.CredentialIssuer;
import org.idp.server.type.verifiablecredential.TransactionId;

public class VerifiableCredentialTransactionCreator {

  VerifiableCredentialDelegateResponse delegateResponse;
  OAuthToken oAuthToken;
  VerifiableCredentialConfiguration verifiableCredentialConfiguration;

  public VerifiableCredentialTransactionCreator(
      VerifiableCredentialDelegateResponse delegateResponse,
      OAuthToken oAuthToken,
      VerifiableCredentialConfiguration verifiableCredentialConfiguration) {
    this.delegateResponse = delegateResponse;
    this.oAuthToken = oAuthToken;
    this.verifiableCredentialConfiguration = verifiableCredentialConfiguration;
  }

  public VerifiableCredentialTransaction create() {
    TransactionId transactionId = new TransactionId(UUID.randomUUID().toString());
    CredentialIssuer credentialIssuer = verifiableCredentialConfiguration.credentialIssuer();
    ClientId clientId = oAuthToken.accessToken().clientId();
    Subject subject = oAuthToken.subject();
    VerifiableCredential verifiableCredential = delegateResponse.credential();
    VerifiableCredentialTransactionStatus status = delegateResponse.status();
    return new VerifiableCredentialTransaction(
        transactionId, credentialIssuer, clientId, subject, verifiableCredential, status);
  }
}
