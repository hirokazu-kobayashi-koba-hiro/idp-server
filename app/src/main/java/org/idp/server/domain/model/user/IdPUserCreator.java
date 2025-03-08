package org.idp.server.domain.model.user;

import java.util.UUID;
import org.idp.server.core.oauth.identity.User;

public class IdPUserCreator {

  UserRegistration userRegistration;
  PasswordEncodeDelegation passwordEncodeDelegation;

  public IdPUserCreator(
      UserRegistration userRegistration, PasswordEncodeDelegation passwordEncodeDelegation) {
    this.userRegistration = userRegistration;
    this.passwordEncodeDelegation = passwordEncodeDelegation;
  }

  public User create() {
    String userId = UUID.randomUUID().toString();
    String hashedPassword = passwordEncodeDelegation.encode(userRegistration.password());

    return new User()
        .setSub(userId)
        .setProviderId("idp-server")
        .setProviderUserId(userId)
        .setEmail(userRegistration.username())
        .setName(userRegistration.username())
        .setHashedPassword(hashedPassword);
  }
}
