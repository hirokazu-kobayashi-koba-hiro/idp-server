package org.idp.server.core.oauth.identity;

import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.token.PasswordCredentialsGrantDelegate;
import org.idp.server.core.type.oauth.Password;
import org.idp.server.core.type.oauth.Username;

public class UserPasswordAuthenticator implements PasswordCredentialsGrantDelegate {

  UserRepository userRepository;
  PasswordVerificationDelegation passwordVerificationDelegation;

  public UserPasswordAuthenticator(
      UserRepository userRepository,
      PasswordVerificationDelegation passwordVerificationDelegation) {
    this.userRepository = userRepository;
    this.passwordVerificationDelegation = passwordVerificationDelegation;
  }

  public User findAndAuthenticate(Tenant tenant, Username username, Password password) {
    User user = userRepository.findBy(tenant, username.value(), "idp-server");
    if (!user.exists()) {
      return User.notFound();
    }

    if (!passwordVerificationDelegation.verify(password.value(), user.hashedPassword())) {
      return User.notFound();
    }

    return user;
  }
}
