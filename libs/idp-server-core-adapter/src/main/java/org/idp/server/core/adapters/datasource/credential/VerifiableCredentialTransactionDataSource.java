package org.idp.server.core.adapters.datasource.credential;

import java.util.Map;
import java.util.Objects;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.type.exception.NotFoundException;
import org.idp.server.core.type.verifiablecredential.TransactionId;
import org.idp.server.core.verifiablecredential.VerifiableCredentialTransaction;
import org.idp.server.core.verifiablecredential.repository.VerifiableCredentialTransactionRepository;

public class VerifiableCredentialTransactionDataSource
    implements VerifiableCredentialTransactionRepository {

  VerifiableCredentialTransactionSqlExecutors executors;

  public VerifiableCredentialTransactionDataSource() {
    this.executors = new VerifiableCredentialTransactionSqlExecutors();
  }

  @Override
  public void register(
      Tenant tenant, VerifiableCredentialTransaction verifiableCredentialTransaction) {
    VerifiableCredentialTransactionSqlExecutor executor = executors.get(tenant.dialect());
    executor.insert(verifiableCredentialTransaction);
  }

  @Override
  public VerifiableCredentialTransaction find(Tenant tenant, TransactionId transactionId) {
    VerifiableCredentialTransactionSqlExecutor executor = executors.get(tenant.dialect());

    Map<String, String> stringMap = executor.selectOne(transactionId);

    if (Objects.isNull(stringMap) || stringMap.isEmpty()) {
      throw new NotFoundException(
          String.format("not found verifiable credential transaction (%s)", transactionId.value()));
    }

    return ModelConverter.convert(stringMap);
  }
}
