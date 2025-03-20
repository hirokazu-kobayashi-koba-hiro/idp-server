package org.idp.server.core.oauth.validator;

import java.util.Objects;
import org.idp.server.core.oauth.authentication.Authentication;
import org.idp.server.core.oauth.exception.OAuthBadRequestException;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.core.type.extension.CustomProperties;

public class OAuthAuthorizeRequestValidator {
  AuthorizationRequestIdentifier authorizationRequestIdentifier;
  User user;
  Authentication authentication;
  CustomProperties customProperties;

  public OAuthAuthorizeRequestValidator(
      AuthorizationRequestIdentifier authorizationRequestIdentifier,
      User user,
      Authentication authentication,
      CustomProperties customProperties) {
    this.authorizationRequestIdentifier = authorizationRequestIdentifier;
    this.user = user;
    this.authentication = authentication;
    this.customProperties = customProperties;
  }

  public void validate() {
    throwExceptionIfNotRequiredParameters();
  }

  void throwExceptionIfNotRequiredParameters() {
    if (Objects.isNull(authorizationRequestIdentifier)
        || !authorizationRequestIdentifier.exists()) {
      throw new OAuthBadRequestException(
          "invalid_request", "authorizationRequestIdentifier is required");
    }
    if (Objects.isNull(user) || !user.exists()) {
      throw new OAuthBadRequestException("invalid_request", "user is required");
    }
    if (Objects.isNull(authentication)) {
      throw new OAuthBadRequestException("invalid_request", "authentication is required");
    }
  }
}
