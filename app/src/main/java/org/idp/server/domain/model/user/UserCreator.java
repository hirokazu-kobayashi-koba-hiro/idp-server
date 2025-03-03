package org.idp.server.domain.model.user;

import java.util.UUID;
import org.idp.server.core.oauth.identity.User;

public class UserCreator {

  UserRegistration userRegistration;
  PasswordEncodeDelegation passwordEncodeDelegation;

  public UserCreator(
      UserRegistration userRegistration, PasswordEncodeDelegation passwordEncodeDelegation) {
    this.userRegistration = userRegistration;
    this.passwordEncodeDelegation = passwordEncodeDelegation;
  }

  public User create() {
    String userId = UUID.randomUUID().toString();
    String hashedPassword = passwordEncodeDelegation.encode(userRegistration.password());

    return new User()
        .setSub(userId)
        .setEmail(userRegistration.username())
        .setName(userRegistration.username())
        .setHashedPassword(hashedPassword);
  }
}
