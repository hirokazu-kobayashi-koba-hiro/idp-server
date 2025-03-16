package org.idp.server.core.user;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.oauth.authentication.Authentication;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.oauth.interaction.OAuthUserInteractionResult;
import org.idp.server.core.oauth.interaction.OAuthUserInteractionType;
import org.idp.server.core.oauth.interaction.OAuthUserInteractor;
import org.idp.server.core.oauth.request.AuthorizationRequest;
import org.idp.server.core.sharedsignal.DefaultEventType;
import org.idp.server.core.tenant.Tenant;

public class UserRegistrationService implements OAuthUserInteractor {

  UserRepository userRepository;
  PasswordEncodeDelegation passwordEncodeDelegation;

  public UserRegistrationService(
      UserRepository userRepository, PasswordEncodeDelegation passwordEncodeDelegation) {
    this.userRepository = userRepository;
    this.passwordEncodeDelegation = passwordEncodeDelegation;
  }

  @Override
  public OAuthUserInteractionResult interact(
      Tenant tenant,
      AuthorizationRequest authorizationRequest,
      OAuthUserInteractionType type,
      Map<String, Object> request,
      UserService userService) {
    String username = (String) request.get("username");
    String password = (String) request.get("password");
    UserRegistration userRegistration = new UserRegistration(username, password);

    User user = create(tenant, userRegistration);
    Authentication authentication = new Authentication();

    User maskedPasswordUser = user.maskPassword();

    Map<String, Object> response = new HashMap<>();
    response.put("user", maskedPasswordUser);
    response.put("authentication", authentication);

    return new OAuthUserInteractionResult(
        type, user, authentication, response, DefaultEventType.user_signup);
  }

  private User create(Tenant tenant, UserRegistration userRegistration) {

    User existingUser = userRepository.findBy(tenant, userRegistration.username(), "idp-server");

    if (existingUser.exists()) {
      throw new UserRegistrationConflictException("User already exists");
    }

    IdPUserCreator idPUserCreator = new IdPUserCreator(userRegistration, passwordEncodeDelegation);
    return idPUserCreator.create();
  }

  public User registerOrUpdate(Tenant tenant, User user) {

    User existingUser = userRepository.findBy(tenant, user.email(), user.providerId());

    if (existingUser.exists()) {
      userRepository.update(user);
      return user;
    }

    userRepository.register(tenant, user);

    return user;
  }
}
