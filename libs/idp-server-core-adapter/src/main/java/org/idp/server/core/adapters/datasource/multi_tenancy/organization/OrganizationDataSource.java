/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.core.adapters.datasource.multi_tenancy.organization;

import java.util.List;
import java.util.Map;
import org.idp.server.platform.multi_tenancy.organization.*;

public class OrganizationDataSource implements OrganizationRepository {

  OrganizationSqlExecutor executor;

  public OrganizationDataSource(OrganizationSqlExecutor executor) {
    this.executor = executor;
  }

  @Override
  public void register(Organization organization) {
    executor.insert(organization);
    if (organization.hasAssignedTenants()) {
      executor.upsertAssignedTenants(organization);
    }
  }

  @Override
  public void update(Organization organization) {
    executor.update(organization);
    if (organization.hasAssignedTenants()) {
      executor.upsertAssignedTenants(organization);
    }
  }

  @Override
  public Organization get(OrganizationIdentifier identifier) {
    Map<String, String> result = executor.selectOne(identifier);
    if (result == null || result.isEmpty()) {
      throw new OrganizationNotFoundException(String.format("Organization not found (%s)", identifier.value()));
    }

    return ModelConvertor.convert(result);
  }

  @Override
  public List<Organization> findList(OrganizationQueries queries) {
    List<Map<String, String>> results = executor.selectList(queries);
    return results.stream().map(ModelConvertor::convert).toList();
  }
}
