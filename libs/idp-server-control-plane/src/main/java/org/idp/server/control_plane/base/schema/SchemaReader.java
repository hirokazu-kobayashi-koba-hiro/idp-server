/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.control_plane.base.schema;

import org.idp.server.basic.json.schema.JsonSchemaDefinition;
import org.idp.server.control_plane.base.schema.resource.ResourceReader;
import org.idp.server.platform.log.LoggerWrapper;

public class SchemaReader {

  private static final LoggerWrapper log = LoggerWrapper.getLogger(SchemaReader.class);

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

  public static JsonSchemaDefinition userSchema() {
    String json = ResourceReader.readClasspath("/schema/1.0/user.json");
    return JsonSchemaDefinition.fromJson(json);
  }

  public static JsonSchemaDefinition tenantInvitationSchema() {
    String json = ResourceReader.readClasspath("/schema/1.0/tenant_invitation.json");
    return JsonSchemaDefinition.fromJson(json);
  }
}
