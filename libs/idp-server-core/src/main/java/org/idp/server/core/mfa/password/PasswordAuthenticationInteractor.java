package org.idp.server.core.mfa.password;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.core.basic.date.SystemDateTime;
import org.idp.server.core.mfa.*;
import org.idp.server.core.oauth.OAuthSession;
import org.idp.server.core.oauth.authentication.Authentication;
import org.idp.server.core.oauth.identity.PasswordVerificationDelegation;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.oauth.identity.UserRepository;
import org.idp.server.core.security.event.DefaultSecurityEventType;
import org.idp.server.core.tenant.Tenant;

public class PasswordAuthenticationInteractor implements MfaInteractor {

  PasswordVerificationDelegation passwordVerificationDelegation;

  public PasswordAuthenticationInteractor(
      PasswordVerificationDelegation passwordVerificationDelegation) {
    this.passwordVerificationDelegation = passwordVerificationDelegation;
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

    User user = userRepository.findBy(tenant, username, "idp-server");
    if (!passwordVerificationDelegation.verify(password, user.hashedPassword())) {

      Map<String, Object> response = new HashMap<>();

      return new MfaInteractionResult(
          type, user, new Authentication(), response, DefaultSecurityEventType.password_failure);
    }

    Authentication authentication =
        new Authentication()
            .setTime(SystemDateTime.now())
            .addMethods(new ArrayList<>(List.of("pwd")))
            .addAcrValues(List.of("urn:mace:incommon:iap:silver"));

    Map<String, Object> response = new HashMap<>();
    response.put("user", user.toMap());
    response.put("authentication", authentication.toMap());

    return new MfaInteractionResult(
        type, user, authentication, response, DefaultSecurityEventType.password_success);
  }
}
