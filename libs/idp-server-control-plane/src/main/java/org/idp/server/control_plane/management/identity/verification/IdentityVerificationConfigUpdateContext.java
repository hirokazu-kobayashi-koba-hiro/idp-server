package org.idp.server.control_plane.management.identity.verification;

import java.util.Map;
import org.idp.server.basic.json.JsonDiffCalculator;
import org.idp.server.basic.json.JsonNodeWrapper;
import org.idp.server.control_plane.management.identity.verification.io.IdentityVerificationConfigManagementResponse;
import org.idp.server.control_plane.management.identity.verification.io.IdentityVerificationConfigManagementStatus;
import org.idp.server.core.identity.verification.IdentityVerificationType;
import org.idp.server.core.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

public class IdentityVerificationConfigUpdateContext {

  Tenant tenant;
  IdentityVerificationConfiguration before;
  IdentityVerificationConfiguration after;
  boolean dryRun;

  public IdentityVerificationConfigUpdateContext(
      Tenant tenant,
      IdentityVerificationConfiguration before,
      IdentityVerificationConfiguration after,
      boolean dryRun) {
    this.tenant = tenant;
    this.before = before;
    this.after = after;
    this.dryRun = dryRun;
  }

  public Tenant tenant() {
    return null;
  }

  public IdentityVerificationConfiguration before() {
    return before;
  }

  public IdentityVerificationConfiguration after() {
    return after;
  }

  public IdentityVerificationType beforeType() {
    return before.type();
  }

  public IdentityVerificationType afterType() {
    return after.type();
  }

  public boolean isDryRun() {
    return dryRun;
  }

  public IdentityVerificationConfigManagementResponse toResponse() {
    JsonNodeWrapper beforeJson = JsonNodeWrapper.fromObject(before.toMap());
    JsonNodeWrapper afterJson = JsonNodeWrapper.fromObject(after.toMap());
    Map<String, Object> diff = JsonDiffCalculator.deepDiff(beforeJson, afterJson);
    Map<String, Object> contents = Map.of("result", after.toMap(), "diff", diff, "dry_run", dryRun);
    return new IdentityVerificationConfigManagementResponse(
        IdentityVerificationConfigManagementStatus.OK, contents);
  }
}
