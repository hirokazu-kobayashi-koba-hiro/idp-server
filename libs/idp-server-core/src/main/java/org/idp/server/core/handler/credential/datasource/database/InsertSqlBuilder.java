package org.idp.server.core.handler.credential.datasource.database;

import org.idp.server.core.basic.sql.SqlBaseBuilder;

class InsertSqlBuilder implements SqlBaseBuilder {
  String sql;
  int columnSize = 6;

  InsertSqlBuilder(String transactionId) {
    this.sql =
        """
                INSERT INTO public.verifiable_credential_transaction
                (transaction_id, credential_issuer, client_id, user_id, verifiable_credential, status)
                VALUES ('$1', '$2', '$3', '$4', '$5', '$6');
                """;
    this.sql = replace(sql, 1, transactionId);
  }

  InsertSqlBuilder setCredentialIssuer(String credentialIssuer) {
    this.sql = replace(sql, 2, credentialIssuer);
    return this;
  }

  InsertSqlBuilder setClientId(String clientId) {
    this.sql = replace(sql, 3, clientId);
    return this;
  }

  InsertSqlBuilder setUserId(String userId) {
    this.sql = replace(sql, 4, userId);
    return this;
  }

  InsertSqlBuilder setVerifiableCredential(String verifiableCredential) {
    this.sql = replace(sql, 5, verifiableCredential);
    return this;
  }

  InsertSqlBuilder setStatus(String status) {
    this.sql = replace(sql, 6, status);
    return this;
  }

  String build() {
    return build(sql, columnSize);
  }
}
