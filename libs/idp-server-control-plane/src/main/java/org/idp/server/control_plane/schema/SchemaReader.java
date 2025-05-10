package org.idp.server.control_plane.schema;

import org.idp.server.basic.json.schema.JsonSchemaDefinition;
import org.idp.server.basic.log.LoggerWrapper;
import org.idp.server.control_plane.schema.resource.ResourceReader;

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
}
