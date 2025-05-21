package org.idp.server.control_plane.management.authentication;

import java.util.Map;
import java.util.UUID;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.basic.json.JsonNodeWrapper;
import org.idp.server.control_plane.management.authentication.io.AuthenticationConfigRequest;
import org.idp.server.core.oidc.authentication.AuthenticationConfiguration;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class AuthenticationConfigRegistrationContextCreator {

  Tenant tenant;
  AuthenticationConfigRequest request;
  boolean dryRun;
  JsonConverter jsonConverter;

  public AuthenticationConfigRegistrationContextCreator(
      Tenant tenant, AuthenticationConfigRequest request, boolean dryRun) {
    this.tenant = tenant;
    this.request = request;
    this.dryRun = dryRun;
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  public AuthenticationConfigRegistrationContext create() {
    JsonNodeWrapper configJson = jsonConverter.readTree(request.toMap());
    String id =
        configJson.contains("id")
            ? configJson.getValueOrEmptyAsString("id")
            : UUID.randomUUID().toString();
    String type = configJson.getValueOrEmptyAsString("type");
    JsonNodeWrapper payloadJson = configJson.getValueAsJsonNode("payload");
    Map<String, Object> payload = payloadJson.toMap();

    AuthenticationConfiguration configuration = new AuthenticationConfiguration(id, type, payload);

    return new AuthenticationConfigRegistrationContext(tenant, configuration, dryRun);
  }
}
