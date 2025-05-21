package org.idp.server.control_plane.management.authentication;

import java.util.Map;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.basic.json.JsonNodeWrapper;
import org.idp.server.control_plane.management.authentication.io.AuthenticationConfigRequest;
import org.idp.server.core.oidc.authentication.AuthenticationConfiguration;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class AuthenticationConfigUpdateContextCreator {

  Tenant tenant;
  AuthenticationConfiguration before;
  AuthenticationConfigRequest request;
  boolean dryRun;
  JsonConverter jsonConverter;

  public AuthenticationConfigUpdateContextCreator(
      Tenant tenant,
      AuthenticationConfiguration before,
      AuthenticationConfigRequest request,
      boolean dryRun) {
    this.tenant = tenant;
    this.before = before;
    this.request = request;
    this.dryRun = dryRun;
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  public AuthenticationConfigUpdateContext create() {
    JsonNodeWrapper configJson = jsonConverter.readTree(request.toMap());
    String id = configJson.getValueOrEmptyAsString("id");
    String type = configJson.getValueOrEmptyAsString("type");
    JsonNodeWrapper payloadJson = configJson.getValueAsJsonNode("payload");
    Map<String, Object> payload = payloadJson.toMap();

    AuthenticationConfiguration after = new AuthenticationConfiguration(id, type, payload);

    return new AuthenticationConfigUpdateContext(tenant, before, after, dryRun);
  }
}
