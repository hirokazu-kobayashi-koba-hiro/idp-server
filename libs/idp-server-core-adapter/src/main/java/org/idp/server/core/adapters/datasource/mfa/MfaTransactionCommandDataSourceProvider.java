package org.idp.server.core.adapters.datasource.mfa;

import org.idp.server.core.mfa.MfaDependencyProvider;
import org.idp.server.core.mfa.MfaTransactionCommandRepository;

public class MfaTransactionCommandDataSourceProvider
    implements MfaDependencyProvider<MfaTransactionCommandRepository> {

  @Override
  public Class<MfaTransactionCommandRepository> type() {
    return MfaTransactionCommandRepository.class;
  }

  @Override
  public MfaTransactionCommandRepository provide() {
    return new MfaTransactionCommandDataSource();
  }
}
