/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.adapters.datasource.multi_tenancy.organization;

import org.idp.server.platform.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.platform.dependency.ApplicationComponentProvider;
import org.idp.server.platform.multi_tenancy.organization.OrganizationRepository;

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
