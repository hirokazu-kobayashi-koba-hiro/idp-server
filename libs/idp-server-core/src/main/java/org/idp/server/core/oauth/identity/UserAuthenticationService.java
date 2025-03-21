package org.idp.server.core.oauth.identity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.core.basic.date.SystemDateTime;
import org.idp.server.core.oauth.OAuthSession;
import org.idp.server.core.oauth.authentication.Authentication;
import org.idp.server.core.oauth.interaction.OAuthUserInteractionResult;
import org.idp.server.core.oauth.interaction.OAuthUserInteractionType;
import org.idp.server.core.oauth.interaction.OAuthUserInteractor;
import org.idp.server.core.sharedsignal.DefaultEventType;
import org.idp.server.core.tenant.Tenant;

public class UserAuthenticationService implements OAuthUserInteractor {

  PasswordVerificationDelegation passwordVerificationDelegation;

  public UserAuthenticationService(PasswordVerificationDelegation passwordVerificationDelegation) {
    this.passwordVerificationDelegation = passwordVerificationDelegation;
  }

  @Override
  public OAuthUserInteractionResult interact(
      Tenant tenant,
      OAuthSession oAuthSession,
      OAuthUserInteractionType type,
      Map<String, Object> request,
      UserRepository userRepository) {
    String username = (String) request.get("username");
    String password = (String) request.get("password");

    User user = authenticateWithPassword(tenant, userRepository, username, password);
    Authentication authentication =
        new Authentication()
            .setTime(SystemDateTime.now())
            .addMethods(new ArrayList<>(List.of("pwd")))
            .addAcrValues(List.of("urn:mace:incommon:iap:silver"));

    Map<String, Object> response = new HashMap<>();
    response.put("user", user.toMap());
    response.put("authentication", authentication.toMap());

    return new OAuthUserInteractionResult(
        type, user, authentication, response, DefaultEventType.password_success);
  }

  private User authenticateWithPassword(
      Tenant tenant, UserRepository userRepository, String username, String password) {
    User user = userRepository.findBy(tenant, username, "idp-server");
    if (!passwordVerificationDelegation.verify(password, user.hashedPassword())) {
      throw new UserNotFoundException("User " + username + " not found");
    }
    return user;
  }
}
