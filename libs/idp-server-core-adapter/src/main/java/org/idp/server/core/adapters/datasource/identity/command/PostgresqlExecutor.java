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

package org.idp.server.core.adapters.datasource.identity.command;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.UserIdentifier;
import org.idp.server.core.openid.identity.UserRole;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

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
                external_user_id,
                external_user_original_payload,
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
                ?
                );
                """;

    List<Object> params = new ArrayList<>();
    params.add(user.subAsUuid());
    params.add(tenant.identifierUUID());
    params.add(user.providerId());
    params.add(user.externalUserId());
    params.add(jsonConverter.write(user.externalProviderOriginalPayload()));
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
                 authentication_devices = ?::jsonb,
                 verified_claims = ?::jsonb,
                 status = ?,
                 updated_at = now()
                 WHERE id = ?::uuid
                 AND tenant_id = ?::uuid;
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
    params.add(jsonConverter.write(user.authenticationDevicesAsList()));
    params.add(jsonConverter.write(user.verifiedClaims()));
    params.add(user.statusName());
    params.add(user.subAsUuid());
    params.add(tenant.identifier().valueAsUuid());

    sqlExecutor.execute(sqlTemplate, params);
  }

  public void updatePassword(Tenant tenant, User user) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
                     UPDATE idp_user
                     SET hashed_password = ?,
                     updated_at = now()
                     WHERE id = ?::uuid
                     AND tenant_id = ?::uuid;
                     """;

    List<Object> params = new ArrayList<>();
    params.add(user.hashedPassword());
    params.add(user.subAsUuid());
    params.add(tenant.identifier().valueAsUuid());

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
    params.add(tenant.identifierUUID());
    params.add(userIdentifier.valueAsUuid());

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public void deleteRoles(Tenant tenant, User user) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String deleteSql =
        """
            DELETE FROM idp_user_roles
            WHERE tenant_id = ?::uuid AND user_id = ?::uuid;
            """;
    List<Object> deleteParams = new ArrayList<>();
    deleteParams.add(tenant.identifierUUID());
    deleteParams.add(user.subAsUuid());
    sqlExecutor.execute(deleteSql, deleteParams);
  }

  @Override
  public void upsertRoles(Tenant tenant, User user) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    List<UserRole> userRoles = user.roles();

    StringBuilder sqlTemplateBuilder = new StringBuilder();
    sqlTemplateBuilder.append(
        """
                        INSERT INTO idp_user_roles (tenant_id, user_id, role_id)
                        VALUES
                        """);

    List<String> sqlValues = new ArrayList<>();
    List<Object> params = new ArrayList<>();

    userRoles.forEach(
        userRole -> {
          sqlValues.add("(?::uuid, ?::uuid, ?::uuid)");
          params.add(tenant.identifierUUID());
          params.add(user.subAsUuid());
          params.add(userRole.roleId());
        });

    sqlTemplateBuilder.append(String.join(",", sqlValues));
    sqlTemplateBuilder.append(" ON CONFLICT DO NOTHING;");

    sqlExecutor.execute(sqlTemplateBuilder.toString(), params);
  }

  @Override
  public void deleteAssignedTenants(Tenant tenant, User user) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String deleteSql =
        """
            DELETE FROM idp_user_assigned_tenants
            WHERE user_id = ?::uuid;
            """;
    List<Object> deleteParams = new ArrayList<>();
    deleteParams.add(user.subAsUuid());
    sqlExecutor.execute(deleteSql, deleteParams);
  }

  @Override
  public void upsertAssignedTenants(Tenant tenant, User user) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    StringBuilder sqlTemplateBuilder = new StringBuilder();
    sqlTemplateBuilder.append(
        """
                        INSERT INTO idp_user_assigned_tenants (tenant_id, user_id)
                        VALUES
                        """);

    List<String> sqlValues = new ArrayList<>();
    List<Object> params = new ArrayList<>();

    List<String> assignedTenants = user.assignedTenants();
    assignedTenants.forEach(
        assignedTenant -> {
          sqlValues.add("(?::uuid, ?::uuid)");
          params.add(assignedTenant);
          params.add(user.subAsUuid());
        });
    sqlTemplateBuilder.append(String.join(",", sqlValues));
    sqlTemplateBuilder.append(" ON CONFLICT (tenant_id, user_id) DO NOTHING;");

    sqlExecutor.execute(sqlTemplateBuilder.toString(), params);
  }

  @Override
  public void deleteCurrentTenant(Tenant tenant, User user) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String deleteSql =
        """
            DELETE FROM idp_user_current_tenant
            WHERE user_id = ?::uuid;
            """;
    List<Object> deleteParams = new ArrayList<>();
    deleteParams.add(user.subAsUuid());
    sqlExecutor.execute(deleteSql, deleteParams);
  }

  @Override
  public void upsertCurrentTenant(Tenant tenant, User user) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
            INSERT INTO idp_user_current_tenant (
            tenant_id,
            user_id
            )
            VALUES (
            ?::uuid,
            ?::uuid
            )
            ON CONFLICT (user_id) DO
            UPDATE SET tenant_id = EXCLUDED.tenant_id, updated_at = now()
            ;
            """;
    List<Object> params = new ArrayList<>();
    params.add(user.currentTenantIdentifier().valueAsUuid());
    params.add(user.subAsUuid());
    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public void deleteAssignedOrganizations(Tenant tenant, User user) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String deleteSql =
        """
            DELETE FROM idp_user_assigned_organizations
            WHERE user_id = ?::uuid;
            """;
    List<Object> deleteParams = new ArrayList<>();
    deleteParams.add(user.subAsUuid());
    sqlExecutor.execute(deleteSql, deleteParams);
  }

  @Override
  public void upsertAssignedOrganizations(Tenant tenant, User user) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    StringBuilder sqlTemplateBuilder = new StringBuilder();
    sqlTemplateBuilder.append(
        """
                            INSERT INTO idp_user_assigned_organizations (organization_id, user_id)
                            VALUES
                            """);

    List<String> sqlValues = new ArrayList<>();
    List<Object> params = new ArrayList<>();

    List<String> assignedOrganizations = user.assignedOrganizations();
    assignedOrganizations.forEach(
        assignedOrganization -> {
          sqlValues.add("(?::uuid, ?::uuid)");
          params.add(assignedOrganization);
          params.add(user.subAsUuid());
        });
    sqlTemplateBuilder.append(String.join(",", sqlValues));
    sqlTemplateBuilder.append(" ON CONFLICT (organization_id, user_id) DO NOTHING;");

    sqlExecutor.execute(sqlTemplateBuilder.toString(), params);
  }

  @Override
  public void deleteCurrentOrganization(Tenant tenant, User user) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String deleteSql =
        """
            DELETE FROM idp_user_current_organization
            WHERE user_id = ?::uuid;
            """;
    List<Object> deleteParams = new ArrayList<>();
    deleteParams.add(user.subAsUuid());
    sqlExecutor.execute(deleteSql, deleteParams);
  }

  @Override
  public void upsertCurrentOrganization(Tenant tenant, User user) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
            INSERT INTO idp_user_current_organization (
            organization_id,
            user_id
            )
            VALUES (
            ?::uuid,
            ?::uuid
            )
            ON CONFLICT (user_id) DO
            UPDATE SET organization_id = EXCLUDED.organization_id, updated_at = now()
            ;
            """;
    List<Object> params = new ArrayList<>();
    params.add(user.currentOrganizationIdentifier().valueAsUuid());
    params.add(user.subAsUuid());
    sqlExecutor.execute(sqlTemplate, params);
  }
}
