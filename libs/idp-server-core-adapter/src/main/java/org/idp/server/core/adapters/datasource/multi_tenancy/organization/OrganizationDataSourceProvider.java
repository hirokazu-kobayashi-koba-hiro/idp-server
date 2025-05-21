package org.idp.server.core.adapters.datasource.multi_tenancy.organization;

import org.idp.server.core.multi_tenancy.organization.OrganizationRepository;
import org.idp.server.platform.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.platform.dependency.ApplicationComponentProvider;

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
