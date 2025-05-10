package org.idp.server.control_plane.definition;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.basic.json.JsonNodeWrapper;
import org.idp.server.basic.log.LoggerWrapper;
import org.idp.server.control_plane.schema.resource.ResourceReader;
import org.idp.server.core.identity.permission.Permission;
import org.idp.server.core.identity.permission.Permissions;
import org.idp.server.core.identity.role.Role;
import org.idp.server.core.identity.role.Roles;

public class DefinitionReader {

  private static final LoggerWrapper log = LoggerWrapper.getLogger(DefinitionReader.class);

  public static void initialValidate() {
    log.info("Definition Registry Initialized");
    new DefinitionReader().permissions();
    log.info("Permissions Definition is valid");
    new DefinitionReader().roles();
    log.info("Roles Definition is valid");
  }

  public static Permissions permissions() {
    String json = ResourceReader.readClasspath("/definition/1.0/permissions.json");
    JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromString(json);
    List<Permission> permissionsList = new ArrayList<>();

    for (JsonNodeWrapper nodeWrapper : jsonNodeWrapper.elements()) {
      String id = nodeWrapper.getValueOrEmptyAsString("id");
      String name = nodeWrapper.getValueOrEmptyAsString("name");
      String description = nodeWrapper.getValueOrEmptyAsString("description");
      permissionsList.add(new Permission(id, name, description));
    }

    return new Permissions(permissionsList);
  }

  public static Roles roles() {
    Permissions permissions = permissions();
    String json = ResourceReader.readClasspath("/definition/1.0/roles.json");
    JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromString(json);
    List<Role> roleList = new ArrayList<>();

    for (JsonNodeWrapper nodeWrapper : jsonNodeWrapper.elements()) {
      String id = nodeWrapper.getValueOrEmptyAsString("id");
      String name = nodeWrapper.getValueOrEmptyAsString("name");
      String description = nodeWrapper.getValueOrEmptyAsString("description");
      List<JsonNodeWrapper> permissionList = nodeWrapper.getValueAsJsonNodeList("permissions");
      List<String> permissionStrings =
          permissionList.stream().map(JsonNodeWrapper::asText).toList();
      Permissions filteredPermissions = permissions.filter(permissionStrings);
      roleList.add(new Role(id, name, description, filteredPermissions.toList()));
    }

    return new Roles(roleList);
  }
}
