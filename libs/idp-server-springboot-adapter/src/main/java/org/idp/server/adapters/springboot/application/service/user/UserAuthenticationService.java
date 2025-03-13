package org.idp.server.adapters.springboot.application.service.user;

import org.idp.server.core.adapters.IdpServerApplication;
import org.idp.server.core.UserManagementApi;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.user.PasswordVerificationDelegation;
import org.idp.server.core.user.UserNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserAuthenticationService implements PasswordVerificationDelegation {

  UserManagementApi userManagementApi;
  PasswordEncoder passwordEncoder;

  public UserAuthenticationService(IdpServerApplication idpServerApplication, PasswordEncoder passwordEncoder) {
    this.userManagementApi = idpServerApplication.userManagementApi();
    this.passwordEncoder = passwordEncoder;
  }

  public User authenticateWithPassword(Tenant tenant, String username, String password) {
    User user = userManagementApi.findBy(tenant, username, "idp-server");
    if (!verify(password, user.hashedPassword())) {
      throw new UserNotFoundException("User " + username + " not found");
    }
    return user;
  }

  @Override
  public boolean verify(String rawPassword, String encodedPassword) {
    return passwordEncoder.matches(rawPassword, encodedPassword);
  }
}
