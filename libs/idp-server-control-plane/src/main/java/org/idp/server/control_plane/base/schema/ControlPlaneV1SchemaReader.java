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

package org.idp.server.control_plane.base.schema;

import org.idp.server.platform.json.schema.JsonSchemaDefinition;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.resource.ResourceReader;

public class ControlPlaneV1SchemaReader {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(ControlPlaneV1SchemaReader.class);

  public static void initialValidate() {
    log.info("Schema Registry Initialized");
    organizationSchema();
    log.info("Organization Schema is valid");
    tenantSchema();
    log.info("Tenant Schema is valid");
    authorizationServerSchema();
    log.info("Authorization Server Schema is valid");
    clientSchema();
    log.info("Client Schema is valid");
    adminUserSchema();
    log.info("Admin User Schema is valid");
    tenantInvitationSchema();
    log.info("Tenant Invitation Schema is valid");
    securityEventHookConfigurationSchema();
    log.info("Security Event Hook Configuration Schema is valid");
  }

  public static JsonSchemaDefinition organizationSchema() {
    String json = ResourceReader.readClasspath("/schema/1.0/organization.json");
    return JsonSchemaDefinition.fromJson(json);
  }

  public static JsonSchemaDefinition tenantSchema() {
    String json = ResourceReader.readClasspath("/schema/1.0/tenant.json");
    return JsonSchemaDefinition.fromJson(json);
  }

  public static JsonSchemaDefinition authorizationServerSchema() {
    String json = ResourceReader.readClasspath("/schema/1.0/authorization-server.json");
    return JsonSchemaDefinition.fromJson(json);
  }

  public static JsonSchemaDefinition clientSchema() {
    String json = ResourceReader.readClasspath("/schema/1.0/client.json");
    return JsonSchemaDefinition.fromJson(json);
  }

  public static JsonSchemaDefinition adminUserSchema() {
    String json = ResourceReader.readClasspath("/schema/1.0/admin-user.json");
    return JsonSchemaDefinition.fromJson(json);
  }

  public static JsonSchemaDefinition adminUserUpdateSchema() {
    String json = ResourceReader.readClasspath("/schema/1.0/admin-user-update.json");
    return JsonSchemaDefinition.fromJson(json);
  }

  public static JsonSchemaDefinition adminUserPasswordSchema() {
    String json = ResourceReader.readClasspath("/schema/1.0/admin-user-password.json");
    return JsonSchemaDefinition.fromJson(json);
  }

  public static JsonSchemaDefinition userSchema() {
    String json = ResourceReader.readClasspath("/schema/1.0/user.json");
    return JsonSchemaDefinition.fromJson(json);
  }

  public static JsonSchemaDefinition tenantInvitationSchema() {
    String json = ResourceReader.readClasspath("/schema/1.0/tenant_invitation.json");
    return JsonSchemaDefinition.fromJson(json);
  }

  public static JsonSchemaDefinition permissionSchema() {
    String json = ResourceReader.readClasspath("/schema/1.0/permission.json");
    return JsonSchemaDefinition.fromJson(json);
  }

  public static JsonSchemaDefinition permissionUpdateSchema() {
    String json = ResourceReader.readClasspath("/schema/1.0/permission-update.json");
    return JsonSchemaDefinition.fromJson(json);
  }

  public static JsonSchemaDefinition roleSchema() {
    String json = ResourceReader.readClasspath("/schema/1.0/role.json");
    return JsonSchemaDefinition.fromJson(json);
  }

  public static JsonSchemaDefinition roleRemovePermissionsSchema() {
    String json = ResourceReader.readClasspath("/schema/1.0/role-remove-permissions.json");
    return JsonSchemaDefinition.fromJson(json);
  }

  public static JsonSchemaDefinition adminUserRolesUpdateSchema() {
    String json = ResourceReader.readClasspath("/schema/1.0/admin-user-roles-update.json");
    return JsonSchemaDefinition.fromJson(json);
  }

  public static JsonSchemaDefinition adminUserTenantAssignmentsUpdateSchema() {
    String json =
        ResourceReader.readClasspath("/schema/1.0/admin-user-tenant-assignments-update.json");
    return JsonSchemaDefinition.fromJson(json);
  }

  public static JsonSchemaDefinition adminUserOrganizationAssignmentsUpdateSchema() {
    String json =
        ResourceReader.readClasspath("/schema/1.0/admin-user-organization-assignments-update.json");
    return JsonSchemaDefinition.fromJson(json);
  }

  public static JsonSchemaDefinition securityEventHookConfigurationSchema() {
    String json =
        ResourceReader.readClasspath("/schema/1.0/security-event-hook-configuration.json");
    return JsonSchemaDefinition.fromJson(json);
  }
}
