package org.idp.server.core.oauth.identity;

import org.idp.server.core.tenant.Tenant;

public class UserRegistrationService {

  UserRepository userRepository;

  public UserRegistrationService(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  public User registerOrUpdate(Tenant tenant, User user) {

    User existingUser = userRepository.findBy(tenant, user.email(), user.providerId());

    if (existingUser.exists()) {
      UserUpdater userUpdater = new UserUpdater(user, existingUser);
      User updatedUser = userUpdater.update();
      userRepository.update(updatedUser);
      return updatedUser;
    }

    userRepository.register(tenant, user);

    return user;
  }
}
