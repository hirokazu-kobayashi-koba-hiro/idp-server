package org.idp.server.adapters.springboot.application.service.user;

import org.idp.server.core.adapters.IdpServerApplication;
import org.idp.server.core.UserManagementApi;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.user.IdPUserCreator;
import org.idp.server.core.user.PasswordEncodeDelegation;
import org.idp.server.core.user.UserRegistration;
import org.idp.server.core.user.UserRegistrationConflictException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserRegistrationService implements PasswordEncodeDelegation {

  UserManagementApi userManagementApi;
  PasswordEncoder passwordEncoder;

  public UserRegistrationService(IdpServerApplication idpServerApplication, PasswordEncoder passwordEncoder) {
    this.userManagementApi = idpServerApplication.userManagementApi();
    this.passwordEncoder = passwordEncoder;
  }

  public User create(Tenant tenant, UserRegistration userRegistration) {

    User existingUser = userManagementApi.findBy(tenant, userRegistration.username(), "idp-server");

    if (existingUser.exists()) {
      throw new UserRegistrationConflictException("User already exists");
    }

    IdPUserCreator idPUserCreator = new IdPUserCreator(userRegistration, this);
    return idPUserCreator.create();
  }

  public User registerOrUpdate(Tenant tenant, User user) {

    User existingUser = userManagementApi.findBy(tenant, user.email(), user.providerId());

    if (existingUser.exists()) {
      userManagementApi.update(user);
      return user;
    }

    userManagementApi.register(tenant, user);

    return user;
  }

  @Override
  public String encode(String rawPassword) {
    return passwordEncoder.encode(rawPassword);
  }
}
