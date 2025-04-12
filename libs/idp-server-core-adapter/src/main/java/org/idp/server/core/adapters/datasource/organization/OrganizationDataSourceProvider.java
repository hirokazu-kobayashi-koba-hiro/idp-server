package org.idp.server.core.adapters.datasource.organization;

import org.idp.server.core.basic.datasource.DataSourceDependencyContainer;
import org.idp.server.core.basic.datasource.DataSourceProvider;
import org.idp.server.core.organization.OrganizationRepository;

public class OrganizationDataSourceProvider implements DataSourceProvider<OrganizationRepository> {

  @Override
  public Class<OrganizationRepository> type() {
    return OrganizationRepository.class;
  }

  @Override
  public OrganizationRepository provide(DataSourceDependencyContainer container) {
    return new OrganizationDataSource();
  }
}
