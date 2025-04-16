package org.idp.server.core.oauth.identity;

import java.util.UUID;

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
        .setEmail(userRegistration.email())
        .setName(userRegistration.username())
        .setHashedPassword(hashedPassword)
        .transitStatus(UserStatus.REGISTERED);
  }
}
