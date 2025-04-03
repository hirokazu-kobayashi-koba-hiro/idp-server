package org.idp.server.core.mfa.password;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.mfa.MfaInteractionResult;
import org.idp.server.core.mfa.MfaInteractionType;
import org.idp.server.core.mfa.MfaInteractor;
import org.idp.server.core.oauth.OAuthSession;
import org.idp.server.core.oauth.authentication.Authentication;
import org.idp.server.core.oauth.identity.*;
import org.idp.server.core.security.event.DefaultSecurityEventType;
import org.idp.server.core.tenant.Tenant;

public class PasswordRegistrationInteractor implements MfaInteractor {

  PasswordEncodeDelegation passwordEncodeDelegation;

  public PasswordRegistrationInteractor(PasswordEncodeDelegation passwordEncodeDelegation) {
    this.passwordEncodeDelegation = passwordEncodeDelegation;
  }

  @Override
  public MfaInteractionResult interact(
      Tenant tenant,
      OAuthSession oAuthSession,
      MfaInteractionType type,
      Map<String, Object> request,
      UserRepository userRepository) {
    String username = (String) request.get("username");
    String password = (String) request.get("password");
    UserRegistration userRegistration = new UserRegistration(username, password);

    User existingUser = userRepository.findBy(tenant, userRegistration.username(), "idp-server");

    if (existingUser.exists()) {
      throw new UserRegistrationConflictException("User already exists");
    }

    IdPUserCreator idPUserCreator = new IdPUserCreator(userRegistration, passwordEncodeDelegation);
    User user = idPUserCreator.create();

    Authentication authentication = new Authentication();

    Map<String, Object> response = new HashMap<>();
    response.put("user", user.toMap());
    response.put("authentication", authentication.toMap());

    return new MfaInteractionResult(
        type, user, authentication, response, DefaultSecurityEventType.user_signup);
  }
}
