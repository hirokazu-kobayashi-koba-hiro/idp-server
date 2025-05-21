package org.idp.server.core.adapters.datasource.ciba.grant;

import org.idp.server.core.ciba.repository.CibaGrantRepository;
import org.idp.server.platform.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.platform.dependency.ApplicationComponentProvider;

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
