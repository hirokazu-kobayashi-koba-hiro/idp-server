package org.idp.server.core.authentication.password;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.core.authentication.*;
import org.idp.server.core.basic.date.SystemDateTime;
import org.idp.server.core.oauth.authentication.Authentication;
import org.idp.server.core.oauth.identity.PasswordVerificationDelegation;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.oauth.identity.UserRepository;
import org.idp.server.core.security.event.DefaultSecurityEventType;
import org.idp.server.core.tenant.Tenant;

public class PasswordAuthenticationInteractor implements AuthenticationInteractor {

  PasswordVerificationDelegation passwordVerificationDelegation;

  public PasswordAuthenticationInteractor(
      PasswordVerificationDelegation passwordVerificationDelegation) {
    this.passwordVerificationDelegation = passwordVerificationDelegation;
  }

  @Override
  public AuthenticationInteractionRequestResult interact(
      Tenant tenant,
      AuthorizationIdentifier authorizationIdentifier,
      AuthenticationInteractionType type,
      AuthenticationInteractionRequest request,
      AuthenticationTransaction transaction,
      UserRepository userRepository) {

    String username = request.optValueAsString("username", "");
    String password = request.optValueAsString("password", "");

    User user = userRepository.findBy(tenant, username, "idp-server");
    if (!passwordVerificationDelegation.verify(password, user.hashedPassword())) {

      Map<String, Object> response = new HashMap<>();
      response.put("error", "invalid_request");
      response.put("error_description", "user is not found or invalid password");

      return new AuthenticationInteractionRequestResult(
          AuthenticationInteractionStatus.CLIENT_ERROR,
          type,
          user,
          new Authentication(),
          response,
          DefaultSecurityEventType.password_failure);
    }

    Authentication authentication =
        new Authentication()
            .setTime(SystemDateTime.now())
            .addMethods(new ArrayList<>(List.of("pwd")))
            .addAcrValues(List.of("urn:mace:incommon:iap:silver"));

    Map<String, Object> response = new HashMap<>();
    response.put("user", user.toMap());
    response.put("authentication", authentication.toMap());

    return new AuthenticationInteractionRequestResult(
        AuthenticationInteractionStatus.SUCCESS,
        type,
        user,
        authentication,
        response,
        DefaultSecurityEventType.password_success);
  }
}
