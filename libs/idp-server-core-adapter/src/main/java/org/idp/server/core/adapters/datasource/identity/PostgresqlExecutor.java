package org.idp.server.core.adapters.datasource.identity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.basic.datasource.SqlExecutor;
import org.idp.server.core.identity.UserIdentifier;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

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
                                 JSON_AGG(DISTINCT role.name)
                                 FILTER (WHERE role.name IS NOT NULL),
                                 '[]'
                               ) AS roles,
                               COALESCE(
                                 JSON_AGG(DISTINCT user_effective_permissions_view.permission_name)
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
