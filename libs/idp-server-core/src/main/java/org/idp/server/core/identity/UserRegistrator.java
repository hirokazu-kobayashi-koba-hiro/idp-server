package org.idp.server.core.identity;

import org.idp.server.core.identity.repository.UserQueryRepository;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

public class UserRegistrator {

  UserQueryRepository userQueryRepository;

  public UserRegistrator(UserQueryRepository userQueryRepository) {
    this.userQueryRepository = userQueryRepository;
  }

  public User registerOrUpdate(Tenant tenant, User user) {

    User existingUser = userQueryRepository.findByEmail(tenant, user.email(), user.providerId());

    if (existingUser.exists()) {
      UserUpdater userUpdater = new UserUpdater(user, existingUser);
      User updatedUser = userUpdater.update();
      userQueryRepository.update(tenant, updatedUser);
      return updatedUser;
    }

    userQueryRepository.register(tenant, user);

    return user;
  }
}
