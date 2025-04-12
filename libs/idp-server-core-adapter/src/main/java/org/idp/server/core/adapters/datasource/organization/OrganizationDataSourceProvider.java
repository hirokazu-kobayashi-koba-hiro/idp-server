package org.idp.server.core.adapters.datasource.organization;

import org.idp.server.core.basic.dependencies.ApplicationComponentDependencyContainer;
import org.idp.server.core.basic.dependencies.ApplicationComponentProvider;
import org.idp.server.core.organization.OrganizationRepository;

public class OrganizationDataSourceProvider
    implements ApplicationComponentProvider<OrganizationRepository> {

  @Override
  public Class<OrganizationRepository> type() {
    return OrganizationRepository.class;
  }

  @Override
  public OrganizationRepository provide(ApplicationComponentDependencyContainer container) {
    return new OrganizationDataSource();
  }
}
