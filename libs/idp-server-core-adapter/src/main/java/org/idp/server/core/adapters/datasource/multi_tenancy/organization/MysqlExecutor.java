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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.multi_tenancy.organization.Organization;
import org.idp.server.platform.multi_tenancy.organization.OrganizationIdentifier;

public class MysqlExecutor implements OrganizationSqlExecutor {

  @Override
  public void insert(Organization organization) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlOrganizationTemplate =
        """
                INSERT INTO organization(id, name, description)
                VALUES (?, ?, ?);
                """;
    List<Object> organizationParams = new ArrayList<>();
    organizationParams.add(organization.identifier().value());
    organizationParams.add(organization.name().value());
    organizationParams.add(organization.description().value());

    sqlExecutor.execute(sqlOrganizationTemplate, organizationParams);
  }

  @Override
  public void upsertAssignedTenants(Organization organization) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    StringBuilder sqlTemplateBuilder =
        new StringBuilder(
            """
                                 INSERT INTO organization_tenants(
                                 organization_id,
                                 tenant_id
                                 )
                                  VALUES
                                """);
    List<String> sqlValues = new ArrayList<>();
    List<Object> tenantParams = new ArrayList<>();

    organization
        .assignedTenants()
        .forEach(
            organizationTenant -> {
              sqlValues.add("(?, ?)");
              tenantParams.add(organization.identifier().value());
              tenantParams.add(organizationTenant.id());
            });
    sqlTemplateBuilder.append(String.join(",", sqlValues));
    sqlTemplateBuilder.append(
        """
              ON DUPLICATE KEY UPDATE assigned_at = now();
            """);
    sqlTemplateBuilder.append(";");

    sqlExecutor.execute(sqlTemplateBuilder.toString(), tenantParams);
  }

  @Override
  public void update(Organization organization) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
                UPDATE organization
                SET name = ?
                description = ?
                WHERE
                id = ?
                """;

    List<Object> organizationParams = new ArrayList<>();
    organizationParams.add(organization.name().value());
    organizationParams.add(organization.description().value());
    organizationParams.add(organization.identifier().value());

    sqlExecutor.execute(sqlTemplate, organizationParams);
  }

  @Override
  public Map<String, String> selectOne(OrganizationIdentifier identifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
                SELECT
                organization.id,
                organization.name,
                organization.description,
                COALESCE(
                JSON_AGG(JSON_BUILD_OBJECT('id', tenant.id, 'name', tenant.name, 'type', tenant.type))
                FILTER (WHERE tenant.id IS NOT NULL),
                '[]'
                ) AS tenants
            FROM organization
                LEFT JOIN organization_tenants ON organization_tenants.organization_id = organization.id
                LEFT JOIN tenant ON organization_tenants.tenant_id = tenant.id
            WHERE organization.id = ?
            GROUP BY
            organization.id,
            organization.name,
            organization.description
          """;
    List<Object> params = new ArrayList<>();
    params.add(identifier.value());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }
}
