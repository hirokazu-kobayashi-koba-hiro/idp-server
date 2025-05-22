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


package org.idp.server.core.adapters.datasource.identity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.core.oidc.identity.UserIdentifier;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class PostgresqlExecutor implements UserSqlExecutor {

  @Override
  public Map<String, String> selectOne(Tenant tenant, UserIdentifier userIdentifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        String.format(selectSql, "WHERE idp_user.tenant_id = ?::uuid AND idp_user.id = ?::uuid");
    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierValue());
    params.add(userIdentifier.value());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectByEmail(Tenant tenant, String email, String providerId) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        String.format(
            selectSql,
            """
                WHERE idp_user.tenant_id = ?::uuid
                AND idp_user.email = ?
                AND idp_user.provider_id = ?
            """);
    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierValue());
    params.add(email);
    params.add(providerId);

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectByPhone(Tenant tenant, String phone, String providerId) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        String.format(
            selectSql,
            """
                        WHERE idp_user.tenant_id = ?::uuid
                        AND idp_user.phone_number = ?
                        AND idp_user.provider_id = ?
                    """);
    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierValue());
    params.add(phone);
    params.add(providerId);

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public List<Map<String, String>> selectList(Tenant tenant, int limit, int offset) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        String.format(
            selectSql, """
                WHERE idp_user.tenant_id = ?::uuid
            """);

    String pagedSql = sqlTemplate + """
            limit ?
            OFFSET ?
            """;

    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierValue());
    params.add(limit);
    params.add(offset);

    return sqlExecutor.selectList(pagedSql, params);
  }

  @Override
  public Map<String, String> selectByProvider(
      Tenant tenant, String providerId, String providerUserId) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        String.format(
            selectSql,
            """
                WHERE
                idp_user.tenant_id = ?::uuid
                AND idp_user.provider_id = ?
                AND idp_user.provider_user_id = ?
            """);
    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierValue());
    params.add(providerId);
    params.add(providerUserId);

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectByAuthenticationDevice(Tenant tenant, String deviceId) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        String.format(
            selectSql,
            """
                        WHERE idp_user.tenant_id = ?::uuid
                        AND EXISTS (
                                  SELECT 1
                                  FROM jsonb_array_elements(idp_user.authentication_devices) AS device
                                  WHERE device->>'id' = ?
                        )
                    """);
    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierValue());
    params.add(deviceId);

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectAssignedOrganization(
      Tenant tenant, UserIdentifier userIdentifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
      SELECT
      COALESCE(
       JSON_AGG(idp_user_assigned_organizations.organization_id)
       FILTER (WHERE idp_user_assigned_organizations.organization_id IS NOT NULL),
       '[]'
     ) AS assigned_organizations,
     idp_user_current_organization.organization_id AS current_organization_id
      FROM idp_user_assigned_organizations
      JOIN idp_user_current_organization
       ON idp_user_assigned_organizations.user_id = idp_user_current_organization.user_id
      WHERE idp_user_assigned_organizations.user_id = ?:: uuid
      GROUP BY
      idp_user_assigned_organizations.user_id,
      idp_user_current_organization.organization_id
      """;

    List<Object> params = new ArrayList<>();
    params.add(userIdentifier.value());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectAssignedTenant(Tenant tenant, UserIdentifier userIdentifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
            SELECT
                COALESCE(
                    JSON_AGG(idp_user_assigned_tenants.tenant_id)
                    FILTER (WHERE idp_user_assigned_tenants.tenant_id IS NOT NULL),
                    '[]'
                ) AS assigned_tenants,
                idp_user_current_tenant.tenant_id AS current_tenant_id
            FROM idp_user_assigned_tenants
                     JOIN idp_user_current_tenant
                          ON idp_user_assigned_tenants.user_id = idp_user_current_tenant.user_id
            WHERE idp_user_current_tenant.user_id = ?::uuid
            GROUP BY
                idp_user_assigned_tenants.user_id,
                idp_user_current_tenant.tenant_id
      """;

    List<Object> params = new ArrayList<>();
    params.add(userIdentifier.value());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  String selectSql =
      """
              SELECT
                  idp_user.id,
                  idp_user.provider_id,
                  idp_user.provider_user_id,
                  idp_user.provider_user_original_payload,
                  idp_user.name,
                  idp_user.given_name,
                  idp_user.family_name,
                  idp_user.middle_name,
                  idp_user.nickname,
                  idp_user.preferred_username,
                  idp_user.profile,
                  idp_user.picture,
                  idp_user.website,
                  idp_user.email,
                  idp_user.email_verified,
                  idp_user.gender,
                  idp_user.birthdate,
                  idp_user.zoneinfo,
                  idp_user.locale,
                  idp_user.phone_number,
                  idp_user.phone_number_verified,
                  idp_user.address,
                  idp_user.custom_properties,
                  idp_user.credentials,
                  idp_user.hashed_password,
                  idp_user.multi_factor_authentication,
                  idp_user.authentication_devices,
                  idp_user.verified_claims,
                  idp_user.status,
                  idp_user.created_at,
                  idp_user.updated_at,
                  COALESCE(
                                  JSON_AGG(JSON_BUILD_OBJECT('role_id', role.id, 'role_name', role.name))
                                  FILTER (WHERE role.id IS NOT NULL),
                                  '[]'
                  ) AS roles,
                  COALESCE(
                                  JSON_AGG(user_effective_permissions_view.permission_name)
                                  FILTER (WHERE user_effective_permissions_view.permission_name IS NOT NULL),
                                  '[]'
                  ) AS permissions
              FROM idp_user
                       LEFT JOIN idp_user_roles ON idp_user.id = idp_user_roles.user_id
                       LEFT JOIN role ON idp_user_roles.role_id = role.id
                       LEFT JOIN user_effective_permissions_view ON idp_user.id = user_effective_permissions_view.user_id
               %s
               GROUP BY
               idp_user.id,
               idp_user.provider_id,
               idp_user.provider_user_id,
               idp_user.provider_user_original_payload,
               idp_user.name,
               idp_user.given_name,
               idp_user.family_name,
               idp_user.middle_name,
               idp_user.nickname,
               idp_user.preferred_username,
               idp_user.profile,
               idp_user.picture,
               idp_user.website,
               idp_user.email,
               idp_user.email_verified,
               idp_user.gender,
               idp_user.birthdate,
               idp_user.zoneinfo,
               idp_user.locale,
               idp_user.phone_number,
               idp_user.phone_number_verified,
               idp_user.address,
               idp_user.custom_properties,
               idp_user.credentials,
               idp_user.hashed_password,
               idp_user.multi_factor_authentication,
               idp_user.status,
               idp_user.created_at,
               idp_user.updated_at
            """;
}
