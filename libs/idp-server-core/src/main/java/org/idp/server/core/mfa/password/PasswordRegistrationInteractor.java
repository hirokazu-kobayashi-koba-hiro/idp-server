package org.idp.server.core.mfa.password;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.mfa.*;
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
      MfaTransactionIdentifier mfaTransactionIdentifier,
      MfaInteractionType type,
      MfaInteractionRequest request,
      OAuthSession oAuthSession,
      UserRepository userRepository) {
    String username = request.optValueAsString("username", "");
    String password = request.optValueAsString("password", "");
    UserRegistration userRegistration = new UserRegistration(username, password);

    User existingUser = userRepository.findBy(tenant, userRegistration.username(), "idp-server");

    if (existingUser.exists()) {

      Map<String, Object> response = new HashMap<>();
      response.put("error", "invalid_request");
      response.put("error_description", "user is conflict with username and password");

      return new MfaInteractionResult(
          MfaInteractionStatus.CLIENT_ERROR,
          type,
          existingUser,
          new Authentication(),
          response,
          DefaultSecurityEventType.user_signup_conflict);
    }

    IdPUserCreator idPUserCreator = new IdPUserCreator(userRegistration, passwordEncodeDelegation);
    User user = idPUserCreator.create();

    Authentication authentication = new Authentication();

    Map<String, Object> response = new HashMap<>();
    response.put("user", user.toMap());
    response.put("authentication", authentication.toMap());

    return new MfaInteractionResult(
        MfaInteractionStatus.SUCCESS,
        type,
        user,
        authentication,
        response,
        DefaultSecurityEventType.user_signup);
  }
}
