package org.idp.server.core.identity.authentication;

import org.idp.server.basic.type.oauth.Password;
import org.idp.server.basic.type.oauth.Username;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.UserRepository;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.token.PasswordCredentialsGrantDelegate;

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
    User user = userRepository.findByEmail(tenant, username.value(), "idp-server");
    if (!user.exists()) {
      return User.notFound();
    }

    if (!passwordVerificationDelegation.verify(password.value(), user.hashedPassword())) {
      return User.notFound();
    }

    return user;
  }
}
