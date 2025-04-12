package org.idp.server.core.adapters.datasource.organization;

import org.idp.server.core.basic.datasource.DataSourceDependencyProvider;
import org.idp.server.core.organization.OrganizationRepository;

public class OrganizationDataSourceProvider
    implements DataSourceDependencyProvider<OrganizationRepository> {

  @Override
  public Class<OrganizationRepository> type() {
    return OrganizationRepository.class;
  }

  @Override
  public OrganizationRepository provide() {
    return new OrganizationDataSource();
  }
}
