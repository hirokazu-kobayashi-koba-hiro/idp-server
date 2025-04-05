package org.idp.server.core.adapters.datasource.mfa;

import org.idp.server.core.mfa.MfaConfigurationQueryRepository;
import org.idp.server.core.mfa.MfaDependencyProvider;

public class MfaConfigurationDataSourceProvider
    implements MfaDependencyProvider<MfaConfigurationQueryRepository> {

  @Override
  public Class<MfaConfigurationQueryRepository> type() {
    return MfaConfigurationQueryRepository.class;
  }

  @Override
  public MfaConfigurationQueryRepository provide() {
    return new MfaConfigurationQueryDataSource();
  }
}
