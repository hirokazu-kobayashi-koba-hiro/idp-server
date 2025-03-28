package org.idp.server.core.adapters.datasource.identity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.basic.sql.SqlExecutor;
import org.idp.server.core.basic.sql.TransactionManager;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.oauth.identity.UserNotFoundException;
import org.idp.server.core.oauth.identity.UserRepository;
import org.idp.server.core.tenant.Tenant;

public class UserDataSource implements UserRepository {

  JsonConverter jsonConverter = JsonConverter.createWithSnakeCaseStrategy();

  @Override
  public void register(Tenant tenant, User user) {
    SqlExecutor sqlExecutor = new SqlExecutor(TransactionManager.getConnection());
    String sqlTemplate =
        """
                INSERT INTO public.idp_user
                (id, tenant_id, provider_id, provider_user_id, provider_user_original_payload, name, given_name, family_name, middle_name, nickname,
                 preferred_username, profile, picture, website, email, email_verified, gender,
                 birthdate, zoneinfo, locale, phone_number, phone_number_verified, address,
                 custom_properties, credentials, hashed_password, multi_factor_authentication, enabled)
                 VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?::jsonb, ?);
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
    params.add(user.isEnabled());

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public User get(String userId) {
    SqlExecutor sqlExecutor = new SqlExecutor(TransactionManager.getConnection());

    String sqlTemplate = String.format(selectSql, "WHERE idp_user.id = ?");
    List<Object> params = new ArrayList<>();
    params.add(userId);

    Map<String, String> result = sqlExecutor.selectOne(sqlTemplate, params);

    if (Objects.isNull(result) || result.isEmpty()) {
      throw new UserNotFoundException(String.format("not found user (%s)", userId));
    }

    return ModelConverter.convert(result);
  }

  @Override
  public User findBy(Tenant tenant, String email, String providerId) {
    SqlExecutor sqlExecutor = new SqlExecutor(TransactionManager.getConnection());

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

    Map<String, String> result = sqlExecutor.selectOne(sqlTemplate, params);

    if (Objects.isNull(result) || result.isEmpty()) {
      return new User();
    }

    return ModelConverter.convert(result);
  }

  @Override
  public List<User> findList(Tenant tenant, int limit, int offset) {
    SqlExecutor sqlExecutor = new SqlExecutor(TransactionManager.getConnection());

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

    List<Map<String, String>> results = sqlExecutor.selectList(pagedSql, params);
    if (Objects.isNull(results) || results.isEmpty()) {
      return List.of();
    }

    return results.stream().map(ModelConverter::convert).collect(Collectors.toList());
  }

  @Override
  public void update(User user) {
    SqlExecutor sqlExecutor = new SqlExecutor(TransactionManager.getConnection());
    String sqlTemplate =
        """
              UPDATE public.idp_user
              SET name = ?, given_name = ?, family_name = ?, middle_name = ?, nickname = ?, preferred_username = ?,
              profile = ?, picture = ?, website = ?, email = ?, email_verified = ?, gender = ?, birthdate = ?, zoneinfo = ?,
              locale = ?, phone_number = ?, phone_number_verified = ?, custom_properties = ?::jsonb, updated_at = now()
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
    params.add(jsonConverter.write(user.customPropertiesValue()));
    params.add(user.sub());

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public User findByProvider(String tenantId, String providerId, String providerUserId) {
    SqlExecutor sqlExecutor = new SqlExecutor(TransactionManager.getConnection());

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
    params.add(tenantId);
    params.add(providerId);
    params.add(providerUserId);

    Map<String, String> result = sqlExecutor.selectOne(sqlTemplate, params);

    if (Objects.isNull(result) || result.isEmpty()) {
      return new User();
    }

    return ModelConverter.convert(result);
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
                               idp_user.enabled,
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
                               idp_user.enabled,
                               idp_user.created_at,
                               idp_user.updated_at
                  """;
}
