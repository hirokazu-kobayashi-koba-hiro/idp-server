package org.idp.server.control_plane.management.federation;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.basic.json.JsonDiffCalculator;
import org.idp.server.basic.json.JsonNodeWrapper;
import org.idp.server.control_plane.management.federation.io.FederationConfigManagementResponse;
import org.idp.server.control_plane.management.federation.io.FederationConfigManagementStatus;
import org.idp.server.core.federation.FederationConfiguration;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class FederationConfigUpdateContext {

  Tenant tenant;
  FederationConfiguration before;
  FederationConfiguration after;
  boolean dryRun;

  public FederationConfigUpdateContext(
      Tenant tenant,
      FederationConfiguration before,
      FederationConfiguration after,
      boolean dryRun) {
    this.tenant = tenant;
    this.before = before;
    this.after = after;
    this.dryRun = dryRun;
  }

  public Tenant tenant() {
    return tenant;
  }

  public FederationConfiguration before() {
    return before;
  }

  public FederationConfiguration after() {
    return after;
  }

  public boolean isDryRun() {
    return dryRun;
  }

  public FederationConfigManagementResponse toResponse() {
    JsonNodeWrapper beforeJson = JsonNodeWrapper.fromObject(before.payload());
    JsonNodeWrapper afterJson = JsonNodeWrapper.fromObject(after.payload());
    Map<String, Object> diff = JsonDiffCalculator.deepDiff(beforeJson, afterJson);
    Map<String, Object> response = new HashMap<>();
    response.put("result", after.toMap());
    response.put("diff", diff);
    response.put("dry_run", dryRun);
    return new FederationConfigManagementResponse(FederationConfigManagementStatus.OK, response);
  }
}
