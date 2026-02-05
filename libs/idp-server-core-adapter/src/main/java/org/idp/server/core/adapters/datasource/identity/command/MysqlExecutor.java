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
import java.util.UUID;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.UserIdentifier;
import org.idp.server.core.openid.identity.UserRole;
import org.idp.server.core.openid.identity.device.AuthenticationDevice;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class MysqlExecutor implements UserCommandSqlExecutor {

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
                ?
                );
                """;

    List<Object> params = new ArrayList<>();
    params.add(user.sub());
    params.add(tenant.identifier().value());
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

    // Sync to idp_user_authentication_devices table
    syncAuthenticationDevices(tenant, user);
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
                 authentication_devices = ?,
                 verified_claims = ?,
                 status = ?,
                 updated_at = now()
                 WHERE id = ?
                 AND tenant_id = ?;
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
    params.add(user.subAsUuid().toString());
    params.add(tenant.identifier().value());

    sqlExecutor.execute(sqlTemplate, params);

    // Sync to idp_user_authentication_devices table
    syncAuthenticationDevices(tenant, user);
  }

  public void updatePassword(Tenant tenant, User user) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
                     UPDATE idp_user
                     SET hashed_password = ?,
                     updated_at = now()
                     WHERE id = ?
                     AND tenant_id = ?;
                     """;

    List<Object> params = new ArrayList<>();
    params.add(user.hashedPassword());
    params.add(user.sub());
    params.add(tenant.identifier().value());

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public void delete(Tenant tenant, UserIdentifier userIdentifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
            DELETE FROM idp_user
            WHERE
            idp_user.tenant_id = ?
            AND idp_user.id = ?;
            """;

    List<Object> params = new ArrayList<>();
    params.add(tenant.identifier().value());
    params.add(userIdentifier.value());

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public void deleteRoles(Tenant tenant, User user) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String deleteSql =
        """
            DELETE FROM idp_user_roles
            WHERE tenant_id = ? AND user_id = ?;
            """;
    List<Object> deleteParams = new ArrayList<>();
    deleteParams.add(tenant.identifier().value());
    deleteParams.add(user.sub());
    sqlExecutor.execute(deleteSql, deleteParams);
  }

  @Override
  public void upsertRoles(Tenant tenant, User user) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    List<UserRole> userRoles = user.roles();

    StringBuilder sqlTemplateBuilder = new StringBuilder();
    sqlTemplateBuilder.append(
        """
                        INSERT IGNORE INTO idp_user_roles (id, tenant_id, user_id, role_id)
                        VALUES
                        """);

    List<String> sqlValues = new ArrayList<>();
    List<Object> params = new ArrayList<>();

    userRoles.forEach(
        userRole -> {
          sqlValues.add("(?, ?, ?, ?)");
          params.add(UUID.randomUUID().toString());
          params.add(tenant.identifier().value());
          params.add(user.subAsUuid().toString());
          params.add(userRole.roleId());
        });

    sqlTemplateBuilder.append(String.join(",", sqlValues));
    sqlTemplateBuilder.append(";");

    sqlExecutor.execute(sqlTemplateBuilder.toString(), params);
  }

  @Override
  public void deleteAssignedTenants(Tenant tenant, User user) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String deleteSql =
        """
            DELETE FROM idp_user_assigned_tenants
            WHERE user_id = ?;
            """;
    List<Object> deleteParams = new ArrayList<>();
    deleteParams.add(user.subAsUuid().toString());
    sqlExecutor.execute(deleteSql, deleteParams);
  }

  @Override
  public void upsertAssignedTenants(Tenant tenant, User user) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    StringBuilder sqlTemplateBuilder = new StringBuilder();
    sqlTemplateBuilder.append(
        """
                        INSERT IGNORE INTO idp_user_assigned_tenants (id, tenant_id, user_id)
                        VALUES
                        """);

    List<String> sqlValues = new ArrayList<>();
    List<Object> params = new ArrayList<>();

    List<String> assignedTenants = user.assignedTenants();
    assignedTenants.forEach(
        assignedTenant -> {
          sqlValues.add("(?, ?, ?)");
          params.add(UUID.randomUUID().toString());
          params.add(assignedTenant);
          params.add(user.subAsUuid().toString());
        });
    sqlTemplateBuilder.append(String.join(",", sqlValues));
    sqlTemplateBuilder.append(";");

    sqlExecutor.execute(sqlTemplateBuilder.toString(), params);
  }

  @Override
  public void deleteCurrentTenant(Tenant tenant, User user) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String deleteSql =
        """
            DELETE FROM idp_user_current_tenant
            WHERE user_id = ?;
            """;
    List<Object> deleteParams = new ArrayList<>();
    deleteParams.add(user.subAsUuid().toString());
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
            ?,
            ?
            )
            ON DUPLICATE KEY
            UPDATE tenant_id = tenant_id, updated_at = now()
            ;
            """;
    List<Object> params = new ArrayList<>();
    params.add(user.currentTenantIdentifier().value());
    params.add(user.subAsUuid().toString());
    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public void deleteAssignedOrganizations(Tenant tenant, User user) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String deleteSql =
        """
            DELETE FROM idp_user_assigned_organizations
            WHERE user_id = ?;
            """;
    List<Object> deleteParams = new ArrayList<>();
    deleteParams.add(user.subAsUuid().toString());
    sqlExecutor.execute(deleteSql, deleteParams);
  }

  @Override
  public void upsertAssignedOrganizations(Tenant tenant, User user) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    StringBuilder sqlTemplateBuilder = new StringBuilder();
    sqlTemplateBuilder.append(
        """
                            INSERT IGNORE INTO idp_user_assigned_organizations (id, organization_id, user_id)
                            VALUES
                            """);

    List<String> sqlValues = new ArrayList<>();
    List<Object> params = new ArrayList<>();

    List<String> assignedOrganizations = user.assignedOrganizations();
    assignedOrganizations.forEach(
        assignedOrganization -> {
          sqlValues.add("(?, ?, ?)");
          params.add(UUID.randomUUID().toString());
          params.add(assignedOrganization);
          params.add(user.subAsUuid().toString());
        });
    sqlTemplateBuilder.append(String.join(",", sqlValues));
    sqlTemplateBuilder.append(";");

    sqlExecutor.execute(sqlTemplateBuilder.toString(), params);
  }

  @Override
  public void deleteCurrentOrganization(Tenant tenant, User user) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String deleteSql =
        """
            DELETE FROM idp_user_current_organization
            WHERE user_id = ?;
            """;
    List<Object> deleteParams = new ArrayList<>();
    deleteParams.add(user.subAsUuid().toString());
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
            ?,
            ?
            )
            ON DUPLICATE KEY
            UPDATE organization_id = organization_id
            ;
            """;
    List<Object> params = new ArrayList<>();
    params.add(user.currentOrganizationIdentifier().value());
    params.add(user.subAsUuid().toString());
    sqlExecutor.execute(sqlTemplate, params);
  }

  /**
   * Sync idp_user_authentication_devices table with idp_user.authentication_devices. Uses DELETE +
   * INSERT for full replacement (efficient since device count is small).
   */
  private void syncAuthenticationDevices(Tenant tenant, User user) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    // Delete existing devices
    String deleteSql =
        """
        DELETE FROM idp_user_authentication_devices
        WHERE user_id = ?
        AND tenant_id = ?
        """;
    List<Object> deleteParams = new ArrayList<>();
    deleteParams.add(user.sub());
    deleteParams.add(tenant.identifier().value());
    sqlExecutor.execute(deleteSql, deleteParams);

    // Insert new devices
    List<AuthenticationDevice> devices = new ArrayList<>();
    user.authenticationDevices().forEach(devices::add);
    if (devices.isEmpty()) {
      return;
    }

    StringBuilder insertSql = new StringBuilder();
    insertSql.append(
        """
        INSERT INTO idp_user_authentication_devices (
            id, tenant_id, user_id, os, model, platform, locale, app_name,
            priority, available_methods, notification_token, notification_channel,
            credential_type, credential_id, credential_payload, credential_metadata
        ) VALUES
        """);

    List<String> valuePlaceholders = new ArrayList<>();
    List<Object> insertParams = new ArrayList<>();

    for (AuthenticationDevice device : devices) {
      valuePlaceholders.add("(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
      insertParams.add(device.id());
      insertParams.add(tenant.identifier().value());
      insertParams.add(user.sub());
      insertParams.add(device.os());
      insertParams.add(device.model());
      insertParams.add(device.platform());
      insertParams.add(device.locale());
      insertParams.add(device.appName());
      insertParams.add(device.priority());
      insertParams.add(jsonConverter.write(device.availableMethods()));
      insertParams.add(device.hasNotificationToken() ? device.notificationToken().value() : null);
      insertParams.add(
          device.hasNotificationChannel() ? device.optNotificationChannel("").name() : null);
      // Integrated credential columns
      insertParams.add(device.hasCredentialType() ? device.credentialType() : null);
      insertParams.add(device.hasCredentialId() ? device.credentialId() : null);
      insertParams.add(
          device.hasCredentialPayload() ? jsonConverter.write(device.credentialPayload()) : null);
      insertParams.add(
          device.hasCredentialMetadata() ? jsonConverter.write(device.credentialMetadata()) : null);
    }

    insertSql.append(String.join(",", valuePlaceholders));
    sqlExecutor.execute(insertSql.toString(), insertParams);
  }
}
