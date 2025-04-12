package org.idp.server.core.adapters.datasource.ciba.grant;

import org.idp.server.core.basic.datasource.DataSourceDependencyProvider;
import org.idp.server.core.ciba.repository.CibaGrantRepository;

public class CibaGrantDataSourceDependencyProvider
    implements DataSourceDependencyProvider<CibaGrantRepository> {

  @Override
  public Class<CibaGrantRepository> type() {
    return CibaGrantRepository.class;
  }

  @Override
  public CibaGrantRepository provide() {
    return new CibaGrantDataSource();
  }
}
