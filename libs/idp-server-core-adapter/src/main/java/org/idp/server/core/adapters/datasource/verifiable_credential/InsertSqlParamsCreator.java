package org.idp.server.core.adapters.datasource.verifiable_credential;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.core.verifiable_credential.VerifiableCredentialTransaction;

class InsertSqlParamsCreator {

  static JsonConverter jsonConverter = JsonConverter.createWithSnakeCaseStrategy();

  static List<Object> create(VerifiableCredentialTransaction verifiableCredentialTransaction) {
    List<Object> params = new ArrayList<>();
    params.add(verifiableCredentialTransaction.transactionId().value());
    params.add(verifiableCredentialTransaction.credentialIssuer().value());
    params.add(verifiableCredentialTransaction.clientId().value());
    params.add(verifiableCredentialTransaction.subject().value());
    params.add(toJson(verifiableCredentialTransaction.verifiableCredential().values()));
    params.add(verifiableCredentialTransaction.status().name());

    return params;
  }

  private static String toJson(Object value) {
    return jsonConverter.write(value);
  }
}
