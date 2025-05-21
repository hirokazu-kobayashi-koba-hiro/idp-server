package org.idp.server.core.identity.authentication;

import org.idp.server.basic.type.oauth.Password;
import org.idp.server.basic.type.oauth.Username;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.repository.UserQueryRepository;
import org.idp.server.core.token.PasswordCredentialsGrantDelegate;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class UserPasswordAuthenticator implements PasswordCredentialsGrantDelegate {

  UserQueryRepository userQueryRepository;
  PasswordVerificationDelegation passwordVerificationDelegation;

  public UserPasswordAuthenticator(
      UserQueryRepository userQueryRepository,
      PasswordVerificationDelegation passwordVerificationDelegation) {
    this.userQueryRepository = userQueryRepository;
    this.passwordVerificationDelegation = passwordVerificationDelegation;
  }

  public User findAndAuthenticate(Tenant tenant, Username username, Password password) {
    User user = userQueryRepository.findByEmail(tenant, username.value(), "idp-server");
    if (!user.exists()) {
      return User.notFound();
    }

    if (!passwordVerificationDelegation.verify(password.value(), user.hashedPassword())) {
      return User.notFound();
    }

    return user;
  }
}
