package org.idp.server.adapters.springboot.application.service.user;

import org.idp.server.adapters.springboot.application.service.user.internal.UserService;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.adapters.springboot.domain.model.tenant.Tenant;
import org.idp.server.adapters.springboot.domain.model.user.IdPUserCreator;
import org.idp.server.adapters.springboot.domain.model.user.PasswordEncodeDelegation;
import org.idp.server.adapters.springboot.domain.model.user.UserRegistration;
import org.idp.server.adapters.springboot.domain.model.user.UserRegistrationConflictException;
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

    User existingUser = userService.findBy(tenant, userRegistration.username(), "idp-server");

    if (existingUser.exists()) {
      throw new UserRegistrationConflictException("User already exists");
    }

    IdPUserCreator idPUserCreator = new IdPUserCreator(userRegistration, this);
    return idPUserCreator.create();
  }

  public User registerOrUpdate(Tenant tenant, User user) {

    User existingUser = userService.findBy(tenant, user.email(), user.providerId());

    if (existingUser.exists()) {
      userService.update(user);
      return user;
    }

    userService.register(tenant, user);

    return user;
  }

  @Override
  public String encode(String rawPassword) {
    return passwordEncoder.encode(rawPassword);
  }
}
