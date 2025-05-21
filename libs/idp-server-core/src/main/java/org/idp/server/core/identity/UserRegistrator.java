package org.idp.server.core.identity;

import org.idp.server.core.identity.repository.UserCommandRepository;
import org.idp.server.core.identity.repository.UserQueryRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class UserRegistrator {

  UserQueryRepository userQueryRepository;
  UserCommandRepository userCommandRepository;

  public UserRegistrator(
      UserQueryRepository userQueryRepository, UserCommandRepository userCommandRepository) {
    this.userQueryRepository = userQueryRepository;
    this.userCommandRepository = userCommandRepository;
  }

  public User registerOrUpdate(Tenant tenant, User user) {

    User existingUser = userQueryRepository.findByEmail(tenant, user.email(), user.providerId());

    if (existingUser.exists()) {
      UserUpdater userUpdater = new UserUpdater(user, existingUser);
      User updatedUser = userUpdater.update();
      userCommandRepository.update(tenant, updatedUser);
      return updatedUser;
    }

    userCommandRepository.register(tenant, user);

    return user;
  }
}
