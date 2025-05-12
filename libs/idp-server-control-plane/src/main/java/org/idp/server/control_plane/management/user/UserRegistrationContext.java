package org.idp.server.control_plane.management.user;

import java.util.Map;
import org.idp.server.control_plane.management.user.io.UserManagementResponse;
import org.idp.server.control_plane.management.user.io.UserManagementStatus;
import org.idp.server.core.identity.User;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

public class UserRegistrationContext {

  User user;
  boolean dryRun;

  public UserRegistrationContext(Tenant tenant, User user, boolean dryRun) {
    this.user = user;
    this.dryRun = dryRun;
  }

  public Tenant tenant() {
    return null;
  }

  public User user() {
    return user;
  }

  public boolean isDryRun() {
    return dryRun;
  }

  public UserManagementResponse toResponse() {
    Map<String, Object> contents = Map.of("user", user.toMap(), "dry_run", dryRun);
    return new UserManagementResponse(UserManagementStatus.OK, contents);
  }
}
