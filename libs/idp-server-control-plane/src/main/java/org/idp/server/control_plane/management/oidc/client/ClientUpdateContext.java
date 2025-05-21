package org.idp.server.control_plane.management.oidc.client;

import java.util.Map;
import org.idp.server.basic.json.JsonDiffCalculator;
import org.idp.server.basic.json.JsonNodeWrapper;
import org.idp.server.control_plane.management.oidc.client.io.ClientManagementResponse;
import org.idp.server.control_plane.management.oidc.client.io.ClientManagementStatus;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class ClientUpdateContext {
  Tenant tenant;
  ClientConfiguration before;
  ClientConfiguration after;
  boolean dryRun;

  public ClientUpdateContext(
      Tenant tenant, ClientConfiguration before, ClientConfiguration after, boolean dryRun) {
    this.tenant = tenant;
    this.before = before;
    this.after = after;
    this.dryRun = dryRun;
  }

  public Tenant tenant() {
    return tenant;
  }

  public ClientConfiguration before() {
    return before;
  }

  public ClientConfiguration after() {
    return after;
  }

  public boolean isDryRun() {
    return dryRun;
  }

  public ClientManagementResponse toResponse() {
    JsonNodeWrapper beforeJson = JsonNodeWrapper.fromObject(before.toMap());
    JsonNodeWrapper afterJson = JsonNodeWrapper.fromObject(after.toMap());
    Map<String, Object> diff = JsonDiffCalculator.deepDiff(beforeJson, afterJson);
    Map<String, Object> contents = Map.of("result", after.toMap(), "diff", diff, "dry_run", dryRun);
    return new ClientManagementResponse(ClientManagementStatus.OK, contents);
  }
}
