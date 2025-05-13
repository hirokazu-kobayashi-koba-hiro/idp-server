package org.idp.server.control_plane.management.authentication;

import java.util.Map;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.basic.json.JsonNodeWrapper;
import org.idp.server.control_plane.management.authentication.io.AuthenticationConfigRegistrationRequest;
import org.idp.server.core.authentication.AuthenticationConfiguration;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

public class AuthenticationConfigUpdateContextCreator {

  Tenant tenant;
  AuthenticationConfiguration before;
  AuthenticationConfigRegistrationRequest request;
  JsonConverter jsonConverter;

  public AuthenticationConfigUpdateContextCreator(
      Tenant tenant,
      AuthenticationConfiguration before,
      AuthenticationConfigRegistrationRequest request) {
    this.tenant = tenant;
    this.before = before;
    this.request = request;
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  public AuthenticationConfigUpdateContext create() {
    JsonNodeWrapper configJson = jsonConverter.readTree(request.get("config"));
    String id = configJson.getValueOrEmptyAsString("id");
    String type = configJson.getValueOrEmptyAsString("type");
    JsonNodeWrapper payloadJson = configJson.getValueAsJsonNode("payload");
    Map<String, Object> payload = payloadJson.toMap();

    AuthenticationConfiguration after = new AuthenticationConfiguration(id, type, payload);

    boolean dryRun = request.isDryRun();

    return new AuthenticationConfigUpdateContext(tenant, before, after, dryRun);
  }
}
