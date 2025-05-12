package org.idp.server.control_plane.management.authentication;

import java.util.Map;
import java.util.UUID;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.basic.json.JsonNodeWrapper;
import org.idp.server.control_plane.management.authentication.io.AuthenticationConfigRegistrationRequest;
import org.idp.server.core.authentication.AuthenticationConfiguration;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

public class AuthenticationConfigRegistrationContextCreator {

  Tenant tenant;
  AuthenticationConfigRegistrationRequest request;
  JsonConverter jsonConverter;

  public AuthenticationConfigRegistrationContextCreator(
      Tenant tenant, AuthenticationConfigRegistrationRequest request) {
    this.tenant = tenant;
    this.request = request;
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  public AuthenticationConfigRegistrationContext create() {
    String id = UUID.randomUUID().toString();
    JsonNodeWrapper configJson = jsonConverter.readTree(request.get("config"));
    String type = configJson.getValueOrEmptyAsString("type");
    JsonNodeWrapper payloadJson = configJson.getValueAsJsonNode("payload");
    Map<String, Object> payload = payloadJson.toMap();

    AuthenticationConfiguration configuration = new AuthenticationConfiguration(id, type, payload);

    boolean dryRun = request.isDryRun();

    return new AuthenticationConfigRegistrationContext(tenant, configuration, dryRun);
  }
}
