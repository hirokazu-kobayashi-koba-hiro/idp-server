/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.adapters.datasource.verifiable_credentials;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.core.extension.verifiable_credentials.VerifiableCredentialTransaction;

class InsertSqlParamsCreator {

  static JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

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
