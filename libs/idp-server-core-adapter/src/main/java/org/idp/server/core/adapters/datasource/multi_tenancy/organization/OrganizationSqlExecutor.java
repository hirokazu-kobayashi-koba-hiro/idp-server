/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.adapters.datasource.multi_tenancy.organization;

import java.util.Map;
import org.idp.server.platform.multi_tenancy.organization.Organization;
import org.idp.server.platform.multi_tenancy.organization.OrganizationIdentifier;

public interface OrganizationSqlExecutor {
  void insert(Organization organization);

  void upsertAssignedTenants(Organization organization);

  void update(Organization organization);

  Map<String, String> selectOne(OrganizationIdentifier identifier);
}
