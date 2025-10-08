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

public class MysqlExecutor implements UserSqlExecutor {

  @Override
  public Map<String, String> selectOne(Tenant tenant, UserIdentifier userIdentifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        String.format(selectSql, "WHERE idp_user.tenant_id = ? AND idp_user.id = ?");
    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierValue());
    params.add(userIdentifier.value());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectByExternalIdpSubject(
      Tenant tenant, String externalSubject, String providerId) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        String.format(
            selectSql,
            """
                        WHERE idp_user.tenant_id = ?
                        AND idp_user.external_user_id = ?
                        AND idp_user.provider_id = ?
                    """);
    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierValue());
    params.add(externalSubject);
    params.add(providerId);

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectByName(Tenant tenant, String name, String providerId) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        String.format(
            selectSql,
            """
                                WHERE idp_user.tenant_id = ?
                                AND idp_user.name = ?
                                AND idp_user.provider_id = ?
                            """);
    List<Object> params = new ArrayList<>();
    params.add(tenant.identifier().value());
    params.add(name);
    params.add(providerId);

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectByDeviceId(
      Tenant tenant, AuthenticationDeviceIdentifier deviceId, String providerId) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        String.format(
            selectSql,
            """
                  WHERE idp_user.tenant_id = ?
                  AND JSON_CONTAINS(COALESCE(idp_user.authentication_devices, JSON_ARRAY()),
                                    JSON_OBJECT('id', ?),
                                    '$')
                  AND idp_user.provider_id = ?
              """);
    List<Object> params = new ArrayList<>();
    params.add(tenant.identifier().value());
    params.add(deviceId.value());
    params.add(providerId);

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectByEmail(Tenant tenant, String email, String providerId) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        String.format(
            selectSql,
            """
                WHERE idp_user.tenant_id = ?
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
                        WHERE idp_user.tenant_id = ?
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
  public Map<String, String> selectCount(Tenant tenant, UserQueries queries) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    StringBuilder where = new StringBuilder("WHERE idp_user.tenant_id = ?");
    List<Object> params = new ArrayList<>();
    params.add(tenant.identifier().value());

    if (queries.hasFrom()) {
      where.append(" AND idp_user.created_at >= ?");
      params.add(queries.from());
    }

    if (queries.hasTo()) {
      where.append(" AND idp_user.created_at <= ?");
      params.add(queries.to());
    }

    if (queries.hasUserId()) {
      where.append(" AND idp_user.id = ?");
      params.add(queries.userId());
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
      where.append(" AND LOWER(idp_user.name) LIKE ?");
      params.add("%" + queries.name().toLowerCase() + "%");
    }

    if (queries.hasGivenName()) {
      where.append(" AND LOWER(idp_user.given_name) LIKE ?");
      params.add("%" + queries.givenName().toLowerCase() + "%");
    }

    if (queries.hasFamilyName()) {
      where.append(" AND LOWER(idp_user.family_name) LIKE ?");
      params.add("%" + queries.familyName().toLowerCase() + "%");
    }

    if (queries.hasMiddleName()) {
      where.append(" AND LOWER(idp_user.middle_name) LIKE ?");
      params.add("%" + queries.middleName().toLowerCase() + "%");
    }
    if (queries.hasNickname()) {
      where.append(" AND LOWER(idp_user.nickname) LIKE ?");
      params.add("%" + queries.nickname().toLowerCase() + "%");
    }
    if (queries.hasPreferredUsername()) {
      where.append(" AND LOWER(idp_user.preferred_username) LIKE ?");
      params.add("%" + queries.preferredUsername().toLowerCase() + "%");
    }

    if (queries.hasPhoneNumber()) {
      where.append(" AND idp_user.phone_number = ?");
      params.add(queries.phoneNumber());
    }

    if (queries.hasRole()) {
      where.append(" AND role.name LIKE ?");
      params.add("%" + queries.role() + "%");
    }

    if (queries.hasPermission()) {
      where.append(" AND user_effective_permissions_view.permission_name LIKE ?");
      params.add("%" + queries.permission() + "%");
    }

    String sql =
        """
    SELECT COUNT(DISTINCT idp_user.id) as count
    FROM idp_user
    LEFT JOIN idp_user_roles ON idp_user.id = idp_user_roles.user_id
    LEFT JOIN role ON idp_user_roles.role_id = role.id
    LEFT JOIN user_effective_permissions_view ON idp_user.id = user_effective_permissions_view.user_id
    """;

    return sqlExecutor.selectOne(sql + where, params);
  }

  @Override
  public List<Map<String, String>> selectList(Tenant tenant, UserQueries queries) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    StringBuilder where = new StringBuilder("WHERE idp_user.tenant_id = ?");
    List<Object> params = new ArrayList<>();
    params.add(tenant.identifier().value());

    if (queries.hasFrom()) {
      where.append(" AND idp_user.created_at >= ?");
      params.add(queries.from());
    }

    if (queries.hasTo()) {
      where.append(" AND idp_user.created_at <= ?");
      params.add(queries.to());
    }

    if (queries.hasUserId()) {
      where.append(" AND idp_user.id = ?");
      params.add(queries.userId());
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
      where.append(" AND LOWER(idp_user.name) LIKE ?");
      params.add("%" + queries.name().toLowerCase() + "%");
    }

    if (queries.hasGivenName()) {
      where.append(" AND LOWER(idp_user.given_name) LIKE ?");
      params.add("%" + queries.givenName().toLowerCase() + "%");
    }

    if (queries.hasFamilyName()) {
      where.append(" AND LOWER(idp_user.family_name) LIKE ?");
      params.add("%" + queries.familyName().toLowerCase() + "%");
    }

    if (queries.hasMiddleName()) {
      where.append(" AND LOWER(idp_user.middle_name) LIKE ?");
      params.add("%" + queries.middleName().toLowerCase() + "%");
    }
    if (queries.hasNickname()) {
      where.append(" AND LOWER(idp_user.nickname) LIKE ?");
      params.add("%" + queries.nickname().toLowerCase() + "%");
    }
    if (queries.hasPreferredUsername()) {
      where.append(" AND LOWER(idp_user.preferred_username) LIKE ?");
      params.add("%" + queries.preferredUsername().toLowerCase() + "%");
    }

    if (queries.hasPhoneNumber()) {
      where.append(" AND idp_user.phone_number = ?");
      params.add(queries.phoneNumber());
    }

    if (queries.hasRole()) {
      where.append(" AND role.name LIKE ?");
      params.add("%" + queries.role() + "%");
    }

    if (queries.hasPermission()) {
      where.append(" AND user_effective_permissions_view.permission_name LIKE ?");
      params.add("%" + queries.permission() + "%");
    }

    String pagedSql =
        String.format(selectSql, where) + """
          LIMIT ?
          OFFSET ?
        """;

    params.add(queries.limit());
    params.add(queries.offset());

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
                idp_user.tenant_id = ?
                AND idp_user.provider_id = ?
                AND idp_user.external_user_id = ?
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
                    JOIN JSON_TABLE(
                                   idp_user.authentication_devices,
                                   '$[*]' COLUMNS (
                                     device_id VARCHAR(255) PATH '$.id'
                                   )
                                 ) AS device
                                 ON device.device_id = ?
                                 WHERE idp_user.tenant_id = ?
                    """);
    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierValue());
    params.add(deviceId);

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectByPreferredUsername(Tenant tenant, String preferredUsername) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        String.format(
            selectSql,
            """
                        WHERE idp_user.tenant_id = ?
                        AND idp_user.preferred_username = ?
                    """);
    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierValue());
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
             COALESCE((
                          SELECT JSON_ARRAYAGG(t.organization_id)
                          FROM (
                                   SELECT DISTINCT idp_user_assigned_organizations.organization_id
                                   FROM idp_user_assigned_organizations
                                   WHERE idp_user_assigned_organizations.user_id = ?
                               ) AS t
                      ), JSON_ARRAY()) AS assigned_organizations,
             idp_user_current_organization.organization_id AS current_organization_id
         FROM idp_user_assigned_organizations
         JOIN idp_user_current_organization on idp_user_current_organization.user_id = idp_user_assigned_organizations.user_id
         WHERE idp_user_assigned_organizations.user_id = ?
         GROUP BY idp_user_assigned_organizations.user_id;
      """;

    List<Object> params = new ArrayList<>();
    params.add(userIdentifier.value());
    params.add(userIdentifier.value());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectAssignedTenant(Tenant tenant, UserIdentifier userIdentifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
          SELECT
              COALESCE((
                           SELECT JSON_ARRAYAGG(t.tenant_id)
                           FROM idp_user_assigned_tenants AS t
                           WHERE t.user_id = ?
                       ), JSON_ARRAY()) AS assigned_tenants,
              idp_user_current_tenant.tenant_id AS current_tenant_id
          FROM idp_user_assigned_tenants
          JOIN idp_user_current_tenant ON idp_user_current_tenant.user_id = idp_user_assigned_tenants.user_id
          WHERE idp_user_assigned_tenants.user_id = ?
          GROUP BY idp_user_assigned_tenants.user_id;
      """;

    List<Object> params = new ArrayList<>();
    params.add(userIdentifier.value());
    params.add(userIdentifier.value());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  String selectSql =
      """
              SELECT
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
                   idp_user.authentication_devices,
                   idp_user.verified_claims,
                   idp_user.status,
                   idp_user.created_at,
                   idp_user.updated_at,
                   COALESCE((
                                SELECT JSON_ARRAYAGG(JSON_OBJECT('role_id', r.id, 'role_name', r.name))
                                FROM (
                                         SELECT DISTINCT role.id AS id, role.name AS name
                                         FROM idp_user_roles
                                                  JOIN role ON role.id = idp_user_roles.role_id
                                         WHERE idp_user_roles.user_id = idp_user.id
                                     ) AS r
                            ), JSON_ARRAY()) AS roles,
                   COALESCE((
                                SELECT JSON_ARRAYAGG(p.permission_name)
                                FROM (
                                         SELECT DISTINCT permission.name AS permission_name
                                         FROM idp_user_roles
                                                  JOIN role_permission ON role_permission.role_id = idp_user_roles.role_id
                                                  JOIN permission ON permission.id = role_permission.permission_id
                                         WHERE idp_user_roles.user_id = idp_user.id
                                     ) AS p
                            ), JSON_ARRAY()) AS permissions
                 FROM idp_user
                 LEFT JOIN idp_user_roles
                         ON idp_user.id = idp_user_roles.user_id
                     LEFT JOIN role
                             ON idp_user_roles.role_id = role.id
                                  LEFT JOIN role_permission
                 ON role.id = role_permission.role_id
                     LEFT JOIN permission
                     ON role_permission.permission_id = permission.id
                 %s
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
                   idp_user.authentication_devices,
                   idp_user.verified_claims,
                   idp_user.status,
                   idp_user.created_at,
                   idp_user.updated_at
                  """;
}
