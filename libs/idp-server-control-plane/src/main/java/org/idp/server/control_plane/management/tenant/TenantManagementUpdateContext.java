package org.idp.server.control_plane.management.tenant;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.basic.json.JsonDiffCalculator;
import org.idp.server.basic.json.JsonNodeWrapper;
import org.idp.server.control_plane.management.tenant.io.TenantManagementResponse;
import org.idp.server.control_plane.management.tenant.io.TenantManagementStatus;
import org.idp.server.core.identity.User;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

public class TenantManagementUpdateContext {

  Tenant adminTenant;
  Tenant before;
  Tenant after;
  User user;
  boolean dryRun;

  public TenantManagementUpdateContext(
      Tenant adminTenant, Tenant before, Tenant after, User user, boolean dryRun) {
    this.adminTenant = adminTenant;
    this.before = before;
    this.after = after;
    this.user = user;
    this.dryRun = dryRun;
  }

  public Tenant adminTenant() {
    return adminTenant;
  }

  public Tenant before() {
    return before;
  }

  public Tenant after() {
    return after;
  }

  public User user() {
    return user;
  }

  public boolean isDryRun() {
    return dryRun;
  }

  public Map<String, Object> diff() {
    JsonNodeWrapper beforeJson = JsonNodeWrapper.fromObject(before.toMap());
    JsonNodeWrapper afterJson = JsonNodeWrapper.fromObject(after.toMap());
    return JsonDiffCalculator.deepDiff(beforeJson, afterJson);
  }

  public TenantManagementResponse toResponse() {
    Map<String, Object> contents = new HashMap<>();
    contents.put("result", after.toMap());
    contents.put("diff", diff());
    contents.put("dry_run", dryRun);
    return new TenantManagementResponse(TenantManagementStatus.OK, contents);
  }
}
