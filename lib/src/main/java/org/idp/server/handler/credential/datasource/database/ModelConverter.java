package org.idp.server.handler.credential.datasource.database;

import java.util.Map;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.basic.vc.Credential;
import org.idp.server.type.oauth.ClientId;
import org.idp.server.type.oauth.Subject;
import org.idp.server.type.verifiablecredential.CredentialIssuer;
import org.idp.server.type.verifiablecredential.TransactionId;
import org.idp.server.verifiablecredential.VerifiableCredentialTransaction;
import org.idp.server.verifiablecredential.VerifiableCredentialTransactionStatus;

class ModelConverter {

  static JsonConverter jsonConverter = JsonConverter.createWithSnakeCaseStrategy();

  static VerifiableCredentialTransaction convert(Map<String, String> stringMap) {
    TransactionId id = new TransactionId(stringMap.get("transaction_id"));
    CredentialIssuer credentialIssuer = new CredentialIssuer(stringMap.get("credential_issuer"));
    ClientId clientId = new ClientId(stringMap.get("client_id"));
    Subject subject = new Subject(stringMap.get("user_id"));
    Credential credential = toVerifiableCredential(stringMap);
    VerifiableCredentialTransactionStatus status =
        VerifiableCredentialTransactionStatus.valueOf(stringMap.get("status"));
    return new VerifiableCredentialTransaction(
        id, credentialIssuer, clientId, subject, credential, status);
  }

  private static Credential toVerifiableCredential(Map<String, String> stringMap) {
    String credential = stringMap.get("verifiable_credential");
    if (credential.isEmpty()) {
      return new Credential();
    }
    return new Credential(jsonConverter.read(credential, Map.class));
  }
}
