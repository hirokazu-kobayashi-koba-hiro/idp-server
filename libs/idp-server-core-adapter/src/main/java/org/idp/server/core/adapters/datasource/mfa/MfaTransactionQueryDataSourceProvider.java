package org.idp.server.core.adapters.datasource.mfa;

import org.idp.server.core.mfa.MfaDependencyProvider;
import org.idp.server.core.mfa.MfaTransactionQueryRepository;

public class MfaTransactionQueryDataSourceProvider
    implements MfaDependencyProvider<MfaTransactionQueryRepository> {

  @Override
  public Class<MfaTransactionQueryRepository> type() {
    return MfaTransactionQueryRepository.class;
  }

  @Override
  public MfaTransactionQueryRepository provide() {
    return new MfaTransactionQueryDataSource();
  }
}
