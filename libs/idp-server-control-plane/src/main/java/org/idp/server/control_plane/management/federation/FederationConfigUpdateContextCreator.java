package org.idp.server.control_plane.management.federation;

import java.util.Map;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.basic.json.JsonNodeWrapper;
import org.idp.server.control_plane.management.federation.io.FederationConfigRequest;
import org.idp.server.core.federation.FederationConfiguration;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

public class FederationConfigUpdateContextCreator {

  Tenant tenant;
  FederationConfiguration before;
  FederationConfigRequest request;
  boolean dryRun;
  JsonConverter jsonConverter;

  public FederationConfigUpdateContextCreator(
      Tenant tenant,
      FederationConfiguration before,
      FederationConfigRequest request,
      boolean dryRun) {
    this.tenant = tenant;
    this.before = before;
    this.request = request;
    this.dryRun = dryRun;
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  public FederationConfigUpdateContext create() {
    JsonNodeWrapper configJson = jsonConverter.readTree(request.toMap());
    String id = configJson.getValueOrEmptyAsString("id");
    String type = configJson.getValueOrEmptyAsString("type");
    JsonNodeWrapper payloadJson = configJson.getValueAsJsonNode("payload");
    Map<String, Object> payload = payloadJson.toMap();

    FederationConfiguration after = new FederationConfiguration(id, type, payload);

    return new FederationConfigUpdateContext(tenant, before, after, dryRun);
  }
}
