package org.idp.server.core.adapters.datasource.ciba.grant;

import org.idp.server.core.basic.datasource.DataSourceDependencyContainer;
import org.idp.server.core.basic.datasource.DataSourceProvider;
import org.idp.server.core.ciba.repository.CibaGrantRepository;

public class CibaGrantDataSourceProvider implements DataSourceProvider<CibaGrantRepository> {

  @Override
  public Class<CibaGrantRepository> type() {
    return CibaGrantRepository.class;
  }

  @Override
  public CibaGrantRepository provide(DataSourceDependencyContainer container) {
    return new CibaGrantDataSource();
  }
}
