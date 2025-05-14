package org.idp.server.control_plane.management.oidc.authorization;

import java.util.Map;
import org.idp.server.basic.json.JsonDiffCalculator;
import org.idp.server.basic.json.JsonNodeWrapper;
import org.idp.server.control_plane.management.oidc.authorization.io.AuthorizationServerManagementResponse;
import org.idp.server.control_plane.management.oidc.authorization.io.AuthorizationServerManagementStatus;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;

public class AuthorizationServerUpdateContext {
  Tenant tenant;
  AuthorizationServerConfiguration before;
  AuthorizationServerConfiguration after;
  boolean dryRun;

  public AuthorizationServerUpdateContext(
      Tenant tenant,
      AuthorizationServerConfiguration before,
      AuthorizationServerConfiguration after,
      boolean dryRun) {
    this.tenant = tenant;
    this.before = before;
    this.after = after;
    this.dryRun = dryRun;
  }

  public Tenant tenant() {
    return tenant;
  }

  public AuthorizationServerConfiguration before() {
    return before;
  }

  public AuthorizationServerConfiguration after() {
    return after;
  }

  public boolean isDryRun() {
    return dryRun;
  }

  public AuthorizationServerManagementResponse toResponse() {
    JsonNodeWrapper beforeJson = JsonNodeWrapper.fromObject(before.toMap());
    JsonNodeWrapper afterJson = JsonNodeWrapper.fromObject(after.toMap());
    Map<String, Object> diff = JsonDiffCalculator.deepDiff(beforeJson, afterJson);
    Map<String, Object> contents = Map.of("diff", diff, "dry_run", dryRun);
    return new AuthorizationServerManagementResponse(
        AuthorizationServerManagementStatus.OK, contents);
  }
}
