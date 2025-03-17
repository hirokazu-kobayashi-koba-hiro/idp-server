package org.idp.server.core.adapters.datasource.credential.database;

import java.util.Map;
import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.basic.vc.Credential;
import org.idp.server.core.type.oauth.ClientId;
import org.idp.server.core.type.oauth.Subject;
import org.idp.server.core.type.verifiablecredential.CredentialIssuer;
import org.idp.server.core.type.verifiablecredential.TransactionId;
import org.idp.server.core.verifiablecredential.VerifiableCredentialTransaction;
import org.idp.server.core.verifiablecredential.VerifiableCredentialTransactionStatus;

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
