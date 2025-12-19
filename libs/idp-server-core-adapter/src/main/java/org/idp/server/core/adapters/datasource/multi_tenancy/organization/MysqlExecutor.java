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
import java.util.UUID;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.multi_tenancy.organization.Organization;
import org.idp.server.platform.multi_tenancy.organization.OrganizationIdentifier;
import org.idp.server.platform.multi_tenancy.organization.OrganizationQueries;

public class MysqlExecutor implements OrganizationSqlExecutor {

  @Override
  public void insert(Organization organization) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlOrganizationTemplate =
        """
                INSERT INTO organization(id, name, description, enabled)
                VALUES (?, ?, ?, ?);
                """;
    List<Object> organizationParams = new ArrayList<>();
    organizationParams.add(organization.identifier().value());
    organizationParams.add(organization.name().value());
    organizationParams.add(organization.description().value());
    organizationParams.add(organization.isEnabled());

    sqlExecutor.execute(sqlOrganizationTemplate, organizationParams);
  }

  @Override
  public void upsertAssignedTenants(Organization organization) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    StringBuilder sqlTemplateBuilder =
        new StringBuilder(
            """
                                 INSERT INTO organization_tenants(
                                 id,
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
              sqlValues.add("(?, ?, ?)");
              tenantParams.add(UUID.randomUUID().toString());
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
                SET name = ?,
                description = ?,
                enabled = ?
                WHERE
                id = ?
                """;

    List<Object> organizationParams = new ArrayList<>();
    organizationParams.add(organization.name().value());
    organizationParams.add(organization.description().value());
    organizationParams.add(organization.isEnabled());
    organizationParams.add(organization.identifier().value());

    sqlExecutor.execute(sqlTemplate, organizationParams);
  }

  @Override
  public void delete(OrganizationIdentifier identifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
                DELETE FROM organization WHERE id = ?
                """;

    List<Object> params = new ArrayList<>();
    params.add(identifier.value());

    sqlExecutor.execute(sqlTemplate, params);
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
              organization.enabled,
              COALESCE(
                JSON_ARRAYAGG(
                  IF(tenant.id IS NOT NULL,
                     JSON_OBJECT('id', tenant.id, 'name', tenant.name, 'type', tenant.type),
                     NULL)
                ),
                JSON_ARRAY()
              ) AS tenants
            FROM organization
            LEFT JOIN organization_tenants
              ON organization_tenants.organization_id = organization.id
            LEFT JOIN tenant
              ON tenant.id = organization_tenants.tenant_id
            WHERE organization.id = ?
            AND organization.enabled = true
            GROUP BY
              organization.id,
              organization.name,
              organization.description,
              organization.enabled;
          """;
    List<Object> params = new ArrayList<>();
    params.add(identifier.valueAsUuid().toString());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public List<Map<String, String>> selectList(OrganizationQueries queries) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    StringBuilder sqlTemplate =
        new StringBuilder(
            """
                SELECT
                organization.id,
                organization.name,
                organization.description,
                organization.enabled,
                COALESCE(
                JSON_ARRAYAGG(JSON_OBJECT('id', tenant.id, 'name', tenant.name, 'type', tenant.type)),
                JSON_ARRAY()
                ) AS tenants
            FROM organization
                LEFT JOIN organization_tenants ON organization_tenants.organization_id = organization.id
                LEFT JOIN tenant ON organization_tenants.tenant_id = tenant.id
            """);

    List<Object> params = new ArrayList<>();

    // Add WHERE clause for specific IDs if provided
    if (queries.hasIds() && !queries.ids().isEmpty()) {
      sqlTemplate.append("WHERE organization.id IN (");
      for (int i = 0; i < queries.ids().size(); i++) {
        if (i > 0) sqlTemplate.append(",");
        sqlTemplate.append("?");
        params.add(queries.ids().get(i));
      }
      sqlTemplate.append(") ");
    }

    sqlTemplate.append(
        """
            GROUP BY
            organization.id,
            organization.name,
            organization.description,
            organization.enabled
            LIMIT ? OFFSET ?
            """);

    params.add(queries.limit());
    params.add(queries.offset());

    return sqlExecutor.selectList(sqlTemplate.toString(), params);
  }
}
