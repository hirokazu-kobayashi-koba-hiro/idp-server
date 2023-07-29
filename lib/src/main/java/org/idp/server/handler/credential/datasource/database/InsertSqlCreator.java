package org.idp.server.handler.credential.datasource.database;

import org.idp.server.basic.json.JsonConverter;
import org.idp.server.verifiablecredential.VerifiableCredentialTransaction;

class InsertSqlCreator {

  static JsonConverter jsonConverter = JsonConverter.createWithSnakeCaseStrategy();

  static String createInsert(VerifiableCredentialTransaction verifiableCredentialTransaction) {
    InsertSqlBuilder builder =
        new InsertSqlBuilder(verifiableCredentialTransaction.transactionId().value())
            .setCredentialIssuer(verifiableCredentialTransaction.credentialIssuer().value())
            .setClientId(verifiableCredentialTransaction.clientId().value())
            .setUserId(verifiableCredentialTransaction.subject().value())
            .setVerifiableCredential(
                toJson(verifiableCredentialTransaction.verifiableCredential().values()))
            .setStatus(verifiableCredentialTransaction.status().name());

    return builder.build();
  }

  private static String toJson(Object value) {
    return jsonConverter.write(value);
  }
}
