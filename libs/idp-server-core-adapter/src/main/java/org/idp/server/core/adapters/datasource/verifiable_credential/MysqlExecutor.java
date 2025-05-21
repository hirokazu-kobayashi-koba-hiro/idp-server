package org.idp.server.core.adapters.datasource.verifiable_credential;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.basic.type.verifiablecredential.TransactionId;
import org.idp.server.core.verifiable_credential.VerifiableCredentialTransaction;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class MysqlExecutor implements VerifiableCredentialTransactionSqlExecutor {

  @Override
  public void insert(
      Tenant tenant, VerifiableCredentialTransaction verifiableCredentialTransaction) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
                    INSERT INTO verifiable_credential_transaction
                    (id, tenant_id, credential_issuer, client_id, user_id, verifiable_credential, status)
                    VALUES (?, ?, ?, ?, ?, ?, ?);
                    """;
    List<Object> params = InsertSqlParamsCreator.create(verifiableCredentialTransaction);

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectOne(Tenant tenant, TransactionId transactionId) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
            SELECT id, credential_issuer, client_id, user_id, verifiable_credential, status
            FROM verifiable_credential_transaction
            WHERE id = ?
            AND tenant_id = ?;
            """;
    List<Object> params = new ArrayList<>();
    params.add(transactionId.value());
    params.add(tenant.identifierValue());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }
}
