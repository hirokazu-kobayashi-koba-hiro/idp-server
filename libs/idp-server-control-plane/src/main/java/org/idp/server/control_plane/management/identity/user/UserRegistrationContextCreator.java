package org.idp.server.control_plane.management.identity.user;

import java.util.UUID;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.control_plane.management.identity.user.io.UserRegistrationRequest;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.UserStatus;
import org.idp.server.core.identity.authentication.PasswordEncodeDelegation;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class UserRegistrationContextCreator {

  Tenant tenant;
  UserRegistrationRequest request;
  boolean dryRun;
  PasswordEncodeDelegation passwordEncodeDelegation;
  JsonConverter jsonConverter;

  public UserRegistrationContextCreator(
      Tenant tenant,
      UserRegistrationRequest request,
      boolean dryRun,
      PasswordEncodeDelegation passwordEncodeDelegation) {
    this.tenant = tenant;
    this.request = request;
    this.dryRun = dryRun;
    this.passwordEncodeDelegation = passwordEncodeDelegation;
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  public UserRegistrationContext create() {
    User user = jsonConverter.read(request.toMap(), User.class);
    if (!user.hasSub()) {
      user.setSub(UUID.randomUUID().toString());
    }
    String encoded = passwordEncodeDelegation.encode(user.rawPassword());
    user.setHashedPassword(encoded);
    user.setStatus(UserStatus.REGISTERED);

    return new UserRegistrationContext(tenant, user, dryRun);
  }
}
