package org.idp.server.core.adapters.datasource.ciba.grant;

import org.idp.server.core.basic.dependencies.ApplicationComponentDependencyContainer;
import org.idp.server.core.basic.dependencies.ApplicationComponentProvider;
import org.idp.server.core.ciba.repository.CibaGrantRepository;

public class CibaGrantDataSourceProvider
    implements ApplicationComponentProvider<CibaGrantRepository> {

  @Override
  public Class<CibaGrantRepository> type() {
    return CibaGrantRepository.class;
  }

  @Override
  public CibaGrantRepository provide(ApplicationComponentDependencyContainer container) {
    return new CibaGrantDataSource();
  }
}
