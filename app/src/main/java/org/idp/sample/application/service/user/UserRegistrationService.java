package org.idp.sample.application.service.user;

import org.idp.sample.application.service.user.internal.UserService;
import org.idp.sample.domain.model.tenant.Tenant;
import org.idp.sample.domain.model.user.PasswordEncodeDelegation;
import org.idp.sample.domain.model.user.UserCreator;
import org.idp.sample.domain.model.user.UserRegistration;
import org.idp.sample.domain.model.user.UserRegistrationConflictException;
import org.idp.server.oauth.identity.User;
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

  public User register(Tenant tenant, UserRegistration userRegistration) {

    User existingUser = userService.findBy(tenant, userRegistration.username());

    if (existingUser.exists()) {
      throw new UserRegistrationConflictException("User already exists");
    }

    UserCreator userCreator = new UserCreator(userRegistration, this);
    User user = userCreator.create();

    userService.register(tenant, user);

    return user;
  }

  @Override
  public String encode(String rawPassword) {
    return passwordEncoder.encode(rawPassword);
  }
}
