package org.idp.server.control_plane.management.security.hook;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.basic.json.JsonDiffCalculator;
import org.idp.server.basic.json.JsonNodeWrapper;
import org.idp.server.control_plane.management.security.hook.io.SecurityEventHookConfigManagementResponse;
import org.idp.server.control_plane.management.security.hook.io.SecurityEventHookConfigManagementStatus;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.hook.SecurityEventHookConfiguration;

public class SecurityEventHookConfigUpdateContext {

  Tenant tenant;
  SecurityEventHookConfiguration before;
  SecurityEventHookConfiguration after;
  boolean dryRun;

  public SecurityEventHookConfigUpdateContext(
      Tenant tenant,
      SecurityEventHookConfiguration before,
      SecurityEventHookConfiguration after,
      boolean dryRun) {
    this.tenant = tenant;
    this.before = before;
    this.after = after;
    this.dryRun = dryRun;
  }

  public Tenant tenant() {
    return tenant;
  }

  public SecurityEventHookConfiguration before() {
    return before;
  }

  public SecurityEventHookConfiguration after() {
    return after;
  }

  public boolean isDryRun() {
    return dryRun;
  }

  public SecurityEventHookConfigManagementResponse toResponse() {
    JsonNodeWrapper beforeJson = JsonNodeWrapper.fromObject(before.payload());
    JsonNodeWrapper afterJson = JsonNodeWrapper.fromObject(after.payload());
    Map<String, Object> diff = JsonDiffCalculator.deepDiff(beforeJson, afterJson);
    Map<String, Object> response = new HashMap<>();
    response.put("result", after.payload());
    response.put("diff", diff);
    response.put("dry_run", dryRun);
    return new SecurityEventHookConfigManagementResponse(
        SecurityEventHookConfigManagementStatus.OK, response);
  }
}
