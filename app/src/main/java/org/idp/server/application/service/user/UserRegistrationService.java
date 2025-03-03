package org.idp.server.application.service.user;

import org.idp.server.application.service.user.internal.UserService;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.domain.model.tenant.Tenant;
import org.idp.server.domain.model.user.PasswordEncodeDelegation;
import org.idp.server.domain.model.user.UserCreator;
import org.idp.server.domain.model.user.UserRegistration;
import org.idp.server.domain.model.user.UserRegistrationConflictException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserRegistrationService implements PasswordEncodeDelegation {

  UserService userService;
  PasswordEncoder passwordEncoder;

  public UserRegistrationService(UserService userService, PasswordEncoder passwordEncoder) {
    this.userService = userService;
    this.passwordEncoder = passwordEncoder;
  }

  public User create(Tenant tenant, UserRegistration userRegistration) {

    User existingUser = userService.findBy(tenant, userRegistration.username());

    if (existingUser.exists()) {
      throw new UserRegistrationConflictException("User already exists");
    }

    UserCreator userCreator = new UserCreator(userRegistration, this);
    return userCreator.create();
  }

  public User register(Tenant tenant, User user) {

    User existingUser = userService.findBy(tenant, user.name());

    if (existingUser.exists()) {
      throw new UserRegistrationConflictException("User already exists");
    }

    userService.register(tenant, user);

    return user;
  }

  @Override
  public String encode(String rawPassword) {
    return passwordEncoder.encode(rawPassword);
  }
}
