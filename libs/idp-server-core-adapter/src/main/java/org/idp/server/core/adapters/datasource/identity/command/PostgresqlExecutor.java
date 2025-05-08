package org.idp.server.core.adapters.datasource.identity.command;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.basic.datasource.SqlExecutor;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.UserIdentifier;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

public class PostgresqlExecutor implements UserCommandSqlExecutor {

  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

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
                ?::uuid,
                ?::uuid,
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
                 address = ?::jsonb,
                 custom_properties = ?::jsonb,
                 multi_factor_authentication = ?::jsonb,
                 authentication_devices = ?::jsonb,
                 verified_claims = ?::jsonb,
                 status = ?,
                 updated_at = now()
                 WHERE id = ?::uuid;
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
  public void delete(Tenant tenant, UserIdentifier userIdentifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
            DELETE FROM idp_user
            WHERE
            idp_user.tenant_id = ?::uuid
            AND idp_user.id = ?::uuid;
            """;

    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierValue());
    params.add(userIdentifier.value());

    sqlExecutor.execute(sqlTemplate, params);
  }
}
