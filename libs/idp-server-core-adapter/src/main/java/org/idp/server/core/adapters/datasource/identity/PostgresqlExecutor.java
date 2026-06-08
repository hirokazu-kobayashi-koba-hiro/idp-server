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
import org.idp.server.core.openid.identity.UserIdentifier;
import org.idp.server.core.openid.identity.UserQueries;
import org.idp.server.core.openid.identity.device.AuthenticationDeviceIdentifier;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.policy.UserAttributeLoadRule;

public class PostgresqlExecutor implements UserSqlExecutor {

  @Override
  public Map<String, String> selectOne(Tenant tenant, UserIdentifier userIdentifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        String.format(
            selectSql(tenant), "WHERE idp_user.tenant_id = ?::uuid AND idp_user.id = ?::uuid");
    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierValue());
    params.add(userIdentifier.valueAsUuid());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectByExternalIdpSubject(
      Tenant tenant, String externalSubject, String providerId) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        String.format(
            selectSql(tenant),
            """
                        WHERE idp_user.tenant_id = ?::uuid
                        AND idp_user.external_user_id = ?
                        AND idp_user.provider_id = ?
                    """);
    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierUUID());
    params.add(externalSubject);
    params.add(providerId);

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectByName(Tenant tenant, String name, String providerId) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        String.format(
            selectSql(tenant),
            """
                                WHERE idp_user.tenant_id = ?::uuid
                                AND idp_user.name = ?
                                AND idp_user.provider_id = ?
                            """);
    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierUUID());
    params.add(name);
    params.add(providerId);

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectByDeviceId(
      Tenant tenant, AuthenticationDeviceIdentifier deviceId, String providerId) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    // Search by device id using subquery on idp_user_authentication_devices table
    String sqlTemplate =
        String.format(
            selectSql(tenant),
            """
                WHERE idp_user.id = (
                    SELECT user_id FROM idp_user_authentication_devices
                    WHERE id = ?::uuid AND tenant_id = ?::uuid
                )
                AND idp_user.provider_id = ?
            """);
    List<Object> params = new ArrayList<>();
    params.add(deviceId.valueAsUuid());
    params.add(tenant.identifierUUID());
    params.add(providerId);

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectByEmail(Tenant tenant, String email, String providerId) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        String.format(
            selectSql(tenant),
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
            selectSql(tenant),
            """
                        WHERE idp_user.tenant_id = ?::uuid
                        AND idp_user.phone_number = ?
                        AND idp_user.provider_id = ?
                    """);
    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierUUID());
    params.add(phone);
    params.add(providerId);

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectCount(Tenant tenant, UserQueries queries) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    StringBuilder where = new StringBuilder("WHERE idp_user.tenant_id = ?::uuid");
    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierUUID());

    if (queries.hasFrom()) {
      where.append(" AND idp_user.created_at >= ?");
      params.add(queries.from());
    }

    if (queries.hasTo()) {
      where.append(" AND idp_user.created_at <= ?");
      params.add(queries.to());
    }

    if (queries.hasUserId()) {
      where.append(" AND idp_user.id = ?::uuid");
      params.add(queries.userIdAsUuid());
    }

    if (queries.hasExternalUserId()) {
      where.append(" AND idp_user.external_user_id = ?");
      params.add(queries.externalUserId());
    }

    if (queries.hasProviderId()) {
      where.append(" AND idp_user.provider_id = ?");
      params.add(queries.providerId());
    }

    if (queries.hasEmail()) {
      where.append(" AND idp_user.email = ?");
      params.add(queries.email());
    }

    if (queries.hasStatus()) {
      where.append(" AND idp_user.status = ?");
      params.add(queries.status().name());
    }

    if (queries.hasName()) {
      where.append(" AND idp_user.name ILIKE ?");
      params.add("%" + queries.name() + "%");
    }

    if (queries.hasGivenName()) {
      where.append(" AND idp_user.given_name ILIKE ?");
      params.add("%" + queries.givenName() + "%");
    }
    if (queries.hasFamilyName()) {
      where.append(" AND idp_user.family_name ILIKE ?");
      params.add("%" + queries.familyName() + "%");
    }
    if (queries.hasMiddleName()) {
      where.append(" AND idp_user.middle_name ILIKE ?");
      params.add("%" + queries.middleName() + "%");
    }
    if (queries.hasNickname()) {
      where.append(" AND idp_user.nickname ILIKE ?");
      params.add("%" + queries.nickname() + "%");
    }
    if (queries.hasPreferredUsername()) {
      where.append(" AND idp_user.preferred_username ILIKE ?");
      params.add("%" + queries.preferredUsername() + "%");
    }

    if (queries.hasPhoneNumber()) {
      where.append(" AND idp_user.phone_number = ?");
      params.add(queries.phoneNumber());
    }

    if (queries.hasRole()) {
      where.append(" AND role.name ILIKE ?");
      params.add("%" + queries.role() + "%");
    }

    if (queries.hasPermission()) {
      where.append(" AND permission.name ILIKE ?");
      params.add("%" + queries.permission() + "%");
    }

    // Mirror selectList: only JOIN role/permission tables when the filter actually uses them.
    // Without this, every count incurs a 4-way LEFT JOIN + COUNT(DISTINCT) even on the common
    // "no role/permission filter" path.
    boolean hasRoleOrPermissionFilter = queries.hasRole() || queries.hasPermission();

    String sql;
    if (hasRoleOrPermissionFilter) {
      sql =
          """
      SELECT COUNT(DISTINCT idp_user.id)
      FROM idp_user
      LEFT JOIN idp_user_roles ON idp_user.id = idp_user_roles.user_id
      LEFT JOIN role ON idp_user_roles.role_id = role.id
      LEFT JOIN role_permission ON role.id = role_permission.role_id
      LEFT JOIN permission ON role_permission.permission_id = permission.id
      """;
    } else {
      sql = "SELECT COUNT(*) FROM idp_user ";
    }

    return sqlExecutor.selectOne(sql + where, params);
  }

  @Override
  public List<Map<String, String>> selectList(Tenant tenant, UserQueries queries) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    StringBuilder cteWhere = new StringBuilder("WHERE idp_user.tenant_id = ?::uuid");
    List<Object> cteParams = new ArrayList<>();
    cteParams.add(tenant.identifierUUID());

    if (queries.hasFrom()) {
      cteWhere.append(" AND idp_user.created_at >= ?");
      cteParams.add(queries.from());
    }

    if (queries.hasTo()) {
      cteWhere.append(" AND idp_user.created_at <= ?");
      cteParams.add(queries.to());
    }

    if (queries.hasUserId()) {
      cteWhere.append(" AND idp_user.id = ?::uuid");
      cteParams.add(queries.userIdAsUuid());
    }

    if (queries.hasExternalUserId()) {
      cteWhere.append(" AND idp_user.external_user_id = ?");
      cteParams.add(queries.externalUserId());
    }

    if (queries.hasProviderId()) {
      cteWhere.append(" AND idp_user.provider_id = ?");
      cteParams.add(queries.providerId());
    }

    if (queries.hasEmail()) {
      cteWhere.append(" AND idp_user.email = ?");
      cteParams.add(queries.email());
    }

    if (queries.hasStatus()) {
      cteWhere.append(" AND idp_user.status = ?");
      cteParams.add(queries.status().name());
    }

    if (queries.hasName()) {
      cteWhere.append(" AND idp_user.name ILIKE ?");
      cteParams.add("%" + queries.name() + "%");
    }

    if (queries.hasGivenName()) {
      cteWhere.append(" AND idp_user.given_name ILIKE ?");
      cteParams.add("%" + queries.givenName() + "%");
    }
    if (queries.hasFamilyName()) {
      cteWhere.append(" AND idp_user.family_name ILIKE ?");
      cteParams.add("%" + queries.familyName() + "%");
    }
    if (queries.hasMiddleName()) {
      cteWhere.append(" AND idp_user.middle_name ILIKE ?");
      cteParams.add("%" + queries.middleName() + "%");
    }
    if (queries.hasNickname()) {
      cteWhere.append(" AND idp_user.nickname ILIKE ?");
      cteParams.add("%" + queries.nickname() + "%");
    }
    if (queries.hasPreferredUsername()) {
      cteWhere.append(" AND idp_user.preferred_username ILIKE ?");
      cteParams.add("%" + queries.preferredUsername() + "%");
    }

    if (queries.hasPhoneNumber()) {
      cteWhere.append(" AND idp_user.phone_number = ?");
      cteParams.add(queries.phoneNumber());
    }

    if (queries.hasRole()) {
      cteWhere.append(" AND role.name ILIKE ?");
      cteParams.add("%" + queries.role() + "%");
    }

    if (queries.hasPermission()) {
      cteWhere.append(" AND permission.name ILIKE ?");
      cteParams.add("%" + queries.permission() + "%");
    }

    cteParams.add(queries.limit());
    cteParams.add(queries.offset());

    boolean hasRoleOrPermissionFilter = queries.hasRole() || queries.hasPermission();

    String cteFrom;
    if (hasRoleOrPermissionFilter) {
      cteFrom =
          """
          SELECT DISTINCT idp_user.id, idp_user.created_at FROM idp_user
          LEFT JOIN idp_user_roles ON idp_user.id = idp_user_roles.user_id
          LEFT JOIN role ON idp_user_roles.role_id = role.id
          LEFT JOIN role_permission ON role.id = role_permission.role_id
          LEFT JOIN permission ON role_permission.permission_id = permission.id
          """;
    } else {
      cteFrom = "SELECT id, created_at FROM idp_user ";
    }

    String cteSql =
        "WITH paged_users AS ("
            + cteFrom
            + cteWhere
            + """
           ORDER BY idp_user.created_at DESC, idp_user.id DESC
           LIMIT ?
           OFFSET ?
        ) """;

    String pagedSql =
        cteSql
            + String.format(selectSql(tenant), "WHERE idp_user.id IN (SELECT id FROM paged_users)")
            + """
          ORDER BY idp_user.created_at DESC, idp_user.id DESC
        """;

    return sqlExecutor.selectList(pagedSql, cteParams);
  }

  @Override
  public Map<String, String> selectByProvider(
      Tenant tenant, String providerId, String providerUserId) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        String.format(
            selectSql(tenant),
            """
                WHERE
                idp_user.tenant_id = ?::uuid
                AND idp_user.provider_id = ?
                AND idp_user.external_user_id = ?
            """);
    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierUUID());
    params.add(providerId);
    params.add(providerUserId);

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectByAuthenticationDevice(Tenant tenant, String deviceId) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    // Search by device id using subquery on idp_user_authentication_devices table
    String sqlTemplate =
        String.format(
            selectSql(tenant),
            """
                WHERE idp_user.id = (
                    SELECT user_id FROM idp_user_authentication_devices
                    WHERE id = ?::uuid AND tenant_id = ?::uuid
                )
            """);
    List<Object> params = new ArrayList<>();
    params.add(deviceId);
    params.add(tenant.identifierUUID());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectByPreferredUsername(
      Tenant tenant, String providerId, String preferredUsername) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        String.format(
            selectSql(tenant),
            """
                        WHERE idp_user.tenant_id = ?::uuid
                        AND idp_user.provider_id = ?
                        AND idp_user.preferred_username = ?
                    """);
    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierUUID());
    params.add(providerId);
    params.add(preferredUsername);

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectByPreferredUsernameNoProvider(
      Tenant tenant, String preferredUsername) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        String.format(
            selectSql(tenant),
            """
                                    WHERE idp_user.tenant_id = ?::uuid
                                    AND idp_user.preferred_username = ?
                                """);
    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierUUID());
    params.add(preferredUsername);

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
      LEFT JOIN idp_user_current_organization
       ON idp_user_assigned_organizations.user_id = idp_user_current_organization.user_id
      WHERE idp_user_assigned_organizations.user_id = ?:: uuid
      GROUP BY
      idp_user_assigned_organizations.user_id,
      idp_user_current_organization.organization_id
      """;

    List<Object> params = new ArrayList<>();
    params.add(userIdentifier.valueAsUuid());

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
                     LEFT JOIN idp_user_current_tenant
                          ON idp_user_assigned_tenants.user_id = idp_user_current_tenant.user_id
            WHERE idp_user_assigned_tenants.user_id = ?::uuid
            GROUP BY
                idp_user_assigned_tenants.user_id,
                idp_user_current_tenant.tenant_id
      """;

    List<Object> params = new ArrayList<>();
    params.add(userIdentifier.valueAsUuid());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectByFidoCredentialId(Tenant tenant, String credentialId) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    // Search by FIDO2 credential_id directly in authentication_devices table
    String sqlTemplate =
        String.format(
            selectSql(tenant),
            """
                WHERE idp_user.id = (
                    SELECT user_id FROM idp_user_authentication_devices
                    WHERE tenant_id = ?::uuid
                    AND credential_id = ?
                )
            """);
    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierUUID());
    params.add(credentialId);

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectStatus(Tenant tenant, UserIdentifier userIdentifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate = "SELECT status FROM idp_user WHERE tenant_id = ?::uuid AND id = ?::uuid";
    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierUUID());
    params.add(userIdentifier.valueAsUuid());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  private static final String SELECT_BASE_FIELDS =
      """
              idp_user.id,
              idp_user.provider_id,
              idp_user.external_user_id,
              idp_user.external_user_original_payload,
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
              (SELECT JSON_AGG(JSON_BUILD_OBJECT(
                  'id', d.id,
                  'os', d.os,
                  'model', d.model,
                  'platform', d.platform,
                  'locale', d.locale,
                  'app_name', d.app_name,
                  'priority', d.priority,
                  'available_methods', d.available_methods,
                  'notification_token', d.notification_token,
                  'notification_channel', d.notification_channel,
                  'credential_type', d.credential_type,
                  'credential_id', d.credential_id,
                  'credential_payload', d.credential_payload,
                  'credential_metadata', d.credential_metadata
              )) FROM idp_user_authentication_devices d WHERE d.user_id = idp_user.id) AS authentication_devices,
              idp_user.verified_claims,
              idp_user.status,
              idp_user.created_at,
              idp_user.updated_at""";

  private static final String SELECT_ROLES_FIELD =
      """
              COALESCE(
                  JSON_AGG(
                      JSON_BUILD_OBJECT('role_id', role.id, 'role_name', role.name)
                  ) FILTER (WHERE role.id IS NOT NULL),
                  '[]'
              ) AS roles""";

  private static final String SELECT_PERMISSIONS_FIELD =
      """
              COALESCE(
                  JSON_AGG(DISTINCT permission.name)
                  FILTER (WHERE permission.id IS NOT NULL),
                  '[]'
              ) AS permissions""";

  private static final String ROLE_JOINS =
      """
          LEFT JOIN idp_user_roles
              ON idp_user.id = idp_user_roles.user_id
          LEFT JOIN role
              ON idp_user_roles.role_id = role.id""";

  private static final String PERMISSION_JOINS =
      """
          LEFT JOIN role_permission
              ON role.id = role_permission.role_id
          LEFT JOIN permission
              ON role_permission.permission_id = permission.id""";

  private static final String GROUP_BY_CLAUSE =
      """
          GROUP BY
              idp_user.id,
              idp_user.provider_id,
              idp_user.external_user_id,
              idp_user.external_user_original_payload,
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
              idp_user.verified_claims,
              idp_user.status,
              idp_user.created_at,
              idp_user.updated_at""";

  /**
   * Builds the user SELECT SQL with {@code %s} placeholder for the WHERE clause, dynamically
   * including or omitting the role / permission JOIN chain and aggregation based on the tenant's
   * {@link UserAttributeLoadRule}.
   *
   * <p>When both roles and permissions are disabled, the SQL skips all four LEFT JOIN clauses and
   * the GROUP BY entirely, which avoids unnecessary JOIN cost on RBAC tables for tenants that do
   * not use role-based access control.
   */
  String selectSql(Tenant tenant) {
    UserAttributeLoadRule rule = tenant.userAttributeLoadRule();

    StringBuilder sb = new StringBuilder();
    sb.append("SELECT\n").append(SELECT_BASE_FIELDS);
    if (rule.includeRoles()) {
      sb.append(",\n").append(SELECT_ROLES_FIELD);
    }
    if (rule.includePermissions()) {
      sb.append(",\n").append(SELECT_PERMISSIONS_FIELD);
    }
    sb.append("\n          FROM idp_user");
    if (rule.needsRoleJoin()) {
      sb.append("\n").append(ROLE_JOINS);
    }
    if (rule.includePermissions()) {
      sb.append("\n").append(PERMISSION_JOINS);
    }
    sb.append("\n          %s");
    if (rule.needsRoleJoin()) {
      sb.append("\n").append(GROUP_BY_CLAUSE);
    }
    sb.append("\n");
    return sb.toString();
  }
}
