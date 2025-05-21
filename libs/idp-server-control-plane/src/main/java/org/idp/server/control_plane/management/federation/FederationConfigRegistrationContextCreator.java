package org.idp.server.control_plane.management.federation;

import java.util.Map;
import java.util.UUID;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.basic.json.JsonNodeWrapper;
import org.idp.server.control_plane.management.federation.io.FederationConfigRequest;
import org.idp.server.core.oidc.federation.FederationConfiguration;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class FederationConfigRegistrationContextCreator {

  Tenant tenant;
  FederationConfigRequest request;
  boolean dryRun;
  JsonConverter jsonConverter;

  public FederationConfigRegistrationContextCreator(
      Tenant tenant, FederationConfigRequest request, boolean dryRun) {
    this.tenant = tenant;
    this.request = request;
    this.dryRun = dryRun;
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  public FederationConfigRegistrationContext create() {
    JsonNodeWrapper configJson = jsonConverter.readTree(request.toMap());
    String id =
        configJson.contains("id")
            ? configJson.getValueOrEmptyAsString("id")
            : UUID.randomUUID().toString();
    String type = configJson.getValueOrEmptyAsString("type");
    String ssoProvider = configJson.getValueOrEmptyAsString("sso_provider");
    JsonNodeWrapper payloadJson = configJson.getValueAsJsonNode("payload");
    Map<String, Object> payload = payloadJson.toMap();

    FederationConfiguration configuration =
        new FederationConfiguration(id, type, ssoProvider, payload);

    return new FederationConfigRegistrationContext(tenant, configuration, dryRun);
  }
}
