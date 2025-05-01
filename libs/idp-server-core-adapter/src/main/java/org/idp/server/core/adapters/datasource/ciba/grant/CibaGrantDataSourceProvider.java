package org.idp.server.core.adapters.datasource.ciba.grant;

import org.idp.server.basic.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.basic.dependency.ApplicationComponentProvider;
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
