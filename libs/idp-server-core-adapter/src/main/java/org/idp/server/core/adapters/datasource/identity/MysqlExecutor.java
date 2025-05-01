package org.idp.server.core.adapters.datasource.identity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.basic.datasource.SqlExecutor;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.core.identity.User;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

public class MysqlExecutor implements UserSqlExecutor {

  JsonConverter jsonConverter = JsonConverter.createWithSnakeCaseStrategy();

  @Override
  public void insert(Tenant tenant, User user) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
                    INSERT INTO idp_user
                    (
                    id,
                    tenant_id,
                    provider_id,
                    provider_user_id,
                    provider_user_original_payload,
                    name,
                    given_name,
                    family_name,
                    middle_name,
                    nickname,
                    preferred_username,
                    profile,
                    picture,
                    website,
                    email,
                    email_verified,
                    gender,
                    birthdate,
                    zoneinfo,
                    locale,
                    phone_number,
                    phone_number_verified,
                    address,
                    custom_properties,
                    credentials,
                    hashed_password,
                    multi_factor_authentication,
                    authentication_devices,
                    verified_claims,
                    status)
                    VALUES
                    (
                    ?,
                    ?,
                    ?,
                    ?,
                    ?::jsonb,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?::jsonb,
                    ?::jsonb,
                    ?::jsonb,
                    ?,
                    ?::jsonb,
                    ?::jsonb,
                    ?::jsonb,
                    ?
                    );
                    """;

    List<Object> params = new ArrayList<>();
    params.add(user.sub());
    params.add(tenant.identifierValue());
    params.add(user.providerId());
    params.add(user.providerUserId());
    params.add(jsonConverter.write(user.providerOriginalPayload()));
    params.add(user.name());
    params.add(user.givenName());
    params.add(user.familyName());
    params.add(user.middleName());
    params.add(user.nickname());
    params.add(user.preferredUsername());
    params.add(user.profile());
    params.add(user.picture());
    params.add(user.website());
    params.add(user.email());
    params.add(user.emailVerified());
    params.add(user.gender());
    params.add(user.birthdate());
    params.add(user.zoneinfo());
    params.add(user.locale());
    params.add(user.phoneNumber());
    params.add(user.phoneNumberVerified());
    params.add(jsonConverter.write(user.address()));
    params.add(jsonConverter.write(user.customProperties().values()));
    params.add(jsonConverter.write(user.verifiableCredentials()));
    params.add(user.hashedPassword());
    params.add(jsonConverter.write(user.multiFactorAuthentication()));
    params.add(jsonConverter.write(user.authenticationDevicesAsList()));
    params.add(jsonConverter.write(user.verifiedClaims()));
    params.add(user.statusName());

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectOne(Tenant tenant, String userId) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate = String.format(selectSql, "WHERE idp_user.id = ?");
    List<Object> params = new ArrayList<>();
    params.add(userId);

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
  public List<Map<String, String>> selectList(Tenant tenant, int limit, int offset) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        String.format(selectSql, """
                WHERE idp_user.tenant_id = ?
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
  public void update(Tenant tenant, User user) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
              UPDATE idp_user
              SET name = ?,
              given_name = ?,
              family_name = ?,
              middle_name = ?,
              nickname = ?,
              preferred_username = ?,
              profile = ?,
              picture = ?,
              website = ?,
              email = ?,
              email_verified = ?,
              gender = ?,
              birthdate = ?,
              zoneinfo = ?,
              locale = ?,
              phone_number = ?,
              phone_number_verified = ?,
              address = ?,
              custom_properties = ?,
              multi_factor_authentication = ?,
              authentication_devices = ?,
              verified_claims = ?,
              status = ?,
              updated_at = now()
              WHERE id = ?;
              """;

    List<Object> params = new ArrayList<>();
    params.add(user.name());
    params.add(user.givenName());
    params.add(user.familyName());
    params.add(user.middleName());
    params.add(user.nickname());
    params.add(user.preferredUsername());
    params.add(user.profile());
    params.add(user.picture());
    params.add(user.website());
    params.add(user.email());
    params.add(user.emailVerified());
    params.add(user.gender());
    params.add(user.birthdate());
    params.add(user.zoneinfo());
    params.add(user.locale());
    params.add(user.phoneNumber());
    params.add(user.phoneNumberVerified());
    params.add(jsonConverter.write(user.address()));
    params.add(jsonConverter.write(user.customPropertiesValue()));
    params.add(jsonConverter.write(user.multiFactorAuthentication()));
    params.add(jsonConverter.write(user.authenticationDevicesAsList()));
    params.add(jsonConverter.write(user.verifiedClaims()));
    params.add(user.statusName());
    params.add(user.sub());

    sqlExecutor.execute(sqlTemplate, params);
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
                               JSON_ARRAYAGG(role.name) roles,
                               JSON_ARRAYAGG(user_effective_permissions_view.permission_name) AS permissions
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
