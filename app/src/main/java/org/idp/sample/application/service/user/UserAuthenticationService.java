package org.idp.sample.application.service.user;

import org.idp.sample.application.service.user.internal.UserService;
import org.idp.sample.domain.model.user.PasswordVerificationDelegation;
import org.idp.server.oauth.identity.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserAuthenticationService implements PasswordVerificationDelegation {

  UserService userService;
  PasswordEncoder passwordEncoder;

  public UserAuthenticationService(UserService userService, PasswordEncoder passwordEncoder) {
    this.userService = userService;
    this.passwordEncoder = passwordEncoder;
  }

  public boolean authenticate(User user) {

    return true;
  }

  @Override
  public boolean verify(String rawPassword, String encodedPassword) {
    return passwordEncoder.matches(rawPassword, encodedPassword);
  }
}
