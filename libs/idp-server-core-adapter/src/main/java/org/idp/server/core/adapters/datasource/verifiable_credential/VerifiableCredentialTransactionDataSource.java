package org.idp.server.core.adapters.datasource.verifiable_credential;

import java.util.Map;
import java.util.Objects;
import org.idp.server.basic.type.verifiablecredential.TransactionId;
import org.idp.server.core.verifiable_credential.VerifiableCredentialTransaction;
import org.idp.server.core.verifiable_credential.repository.VerifiableCredentialTransactionRepository;
import org.idp.server.platform.exception.NotFoundException;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class VerifiableCredentialTransactionDataSource
    implements VerifiableCredentialTransactionRepository {

  VerifiableCredentialTransactionSqlExecutors executors;

  public VerifiableCredentialTransactionDataSource() {
    this.executors = new VerifiableCredentialTransactionSqlExecutors();
  }

  @Override
  public void register(
      Tenant tenant, VerifiableCredentialTransaction verifiableCredentialTransaction) {
    VerifiableCredentialTransactionSqlExecutor executor = executors.get(tenant.databaseType());
    executor.insert(tenant, verifiableCredentialTransaction);
  }

  @Override
  public VerifiableCredentialTransaction find(Tenant tenant, TransactionId transactionId) {
    VerifiableCredentialTransactionSqlExecutor executor = executors.get(tenant.databaseType());

    Map<String, String> stringMap = executor.selectOne(tenant, transactionId);

    if (Objects.isNull(stringMap) || stringMap.isEmpty()) {
      throw new NotFoundException(
          String.format("not found verifiable credential transaction (%s)", transactionId.value()));
    }

    return ModelConverter.convert(stringMap);
  }
}
