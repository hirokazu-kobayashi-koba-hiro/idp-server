package org.idp.server.adapters.springboot.application.service.user;

import org.idp.server.core.adapters.IdpServerApplication;
import org.idp.server.core.UserManagementApi;
import org.idp.server.core.oauth.authentication.Authentication;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.oauth.interaction.OAuthUserInteractionResult;
import org.idp.server.core.oauth.interaction.OAuthUserInteractionType;
import org.idp.server.core.oauth.interaction.OAuthUserInteractor;
import org.idp.server.core.oauth.request.AuthorizationRequest;
import org.idp.server.core.sharedsignal.DefaultEventType;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.user.IdPUserCreator;
import org.idp.server.core.user.PasswordEncodeDelegation;
import org.idp.server.core.user.UserRegistration;
import org.idp.server.core.user.UserRegistrationConflictException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class UserRegistrationService implements OAuthUserInteractor, PasswordEncodeDelegation {

  UserManagementApi userManagementApi;
  PasswordEncoder passwordEncoder;

  public UserRegistrationService(IdpServerApplication idpServerApplication, PasswordEncoder passwordEncoder) {
    this.userManagementApi = idpServerApplication.userManagementApi();
    this.passwordEncoder = passwordEncoder;
  }

  @Override
  public OAuthUserInteractionResult interact(Tenant tenant, AuthorizationRequest authorizationRequest, OAuthUserInteractionType type, Map<String, Object> request) {
    String username = (String) request.get("username");
    String password = (String) request.get("password");
    UserRegistration userRegistration = new UserRegistration(username, password);

    User user = create(tenant, userRegistration);
    Authentication authentication = new Authentication();

    User maskedPasswordUser = user.maskPassword();

    Map<String, Object> response = new HashMap<>();
    response.put("user", maskedPasswordUser);
    response.put("authentication", authentication);

    return new OAuthUserInteractionResult(type, user, authentication, response, DefaultEventType.user_signup);
  }

  private User create(Tenant tenant, UserRegistration userRegistration) {

    User existingUser = userManagementApi.findBy(tenant, userRegistration.username(), "idp-server");

    if (existingUser.exists()) {
      throw new UserRegistrationConflictException("User already exists");
    }

    IdPUserCreator idPUserCreator = new IdPUserCreator(userRegistration, this);
    return idPUserCreator.create();
  }

  public User registerOrUpdate(Tenant tenant, User user) {

    User existingUser = userManagementApi.findBy(tenant, user.email(), user.providerId());

    if (existingUser.exists()) {
      userManagementApi.update(user);
      return user;
    }

    userManagementApi.register(tenant, user);

    return user;
  }

  @Override
  public String encode(String rawPassword) {
    return passwordEncoder.encode(rawPassword);
  }


}
