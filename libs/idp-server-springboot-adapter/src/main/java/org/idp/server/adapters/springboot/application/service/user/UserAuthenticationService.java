package org.idp.server.adapters.springboot.application.service.user;

import org.idp.server.core.adapters.IdpServerApplication;
import org.idp.server.core.UserManagementApi;
import org.idp.server.core.basic.date.SystemDateTime;
import org.idp.server.core.oauth.authentication.Authentication;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.oauth.interaction.OAuthUserInteractionResult;
import org.idp.server.core.oauth.interaction.OAuthUserInteractionType;
import org.idp.server.core.oauth.interaction.OAuthUserInteractor;
import org.idp.server.core.oauth.request.AuthorizationRequest;
import org.idp.server.core.sharedsignal.DefaultEventType;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.user.PasswordVerificationDelegation;
import org.idp.server.core.user.UserNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class UserAuthenticationService implements OAuthUserInteractor, PasswordVerificationDelegation {

  UserManagementApi userManagementApi;
  PasswordEncoder passwordEncoder;

  public UserAuthenticationService(IdpServerApplication idpServerApplication, PasswordEncoder passwordEncoder) {
    this.userManagementApi = idpServerApplication.userManagementApi();
    this.passwordEncoder = passwordEncoder;
  }

  @Override
  public OAuthUserInteractionResult interact(Tenant tenant, AuthorizationRequest authorizationRequest, OAuthUserInteractionType type, Map<String, Object> request) {
    String username = (String) request.get("username");
    String password = (String) request.get("password");

    User user = authenticateWithPassword(tenant, username, password);
    Authentication authentication =
            new Authentication()
                    .setTime(SystemDateTime.now())
                    .addMethods(new ArrayList<>(List.of("pwd")))
                    .addAcrValues(List.of("urn:mace:incommon:iap:silver"));

    Map<String, Object> response = Map.of("user", user);

    return new OAuthUserInteractionResult(type, user, authentication, response, DefaultEventType.password_success);
  }

  private User authenticateWithPassword(Tenant tenant, String username, String password) {
    User user = userManagementApi.findBy(tenant, username, "idp-server");
    if (!verify(password, user.hashedPassword())) {
      throw new UserNotFoundException("User " + username + " not found");
    }
    return user;
  }

  @Override
  public boolean verify(String rawPassword, String encodedPassword) {
    return passwordEncoder.matches(rawPassword, encodedPassword);
  }


}
