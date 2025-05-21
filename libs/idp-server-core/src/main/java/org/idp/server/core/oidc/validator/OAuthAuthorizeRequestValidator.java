package org.idp.server.core.oidc.validator;

import java.util.Objects;
import org.idp.server.basic.type.extension.CustomProperties;
import org.idp.server.core.oidc.authentication.Authentication;
import org.idp.server.core.oidc.exception.OAuthAuthorizeBadRequestException;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.request.AuthorizationRequestIdentifier;

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
      throw new OAuthAuthorizeBadRequestException(
          "invalid_request", "authorizationRequestIdentifier is required");
    }
    if (Objects.isNull(user) || !user.exists()) {
      throw new OAuthAuthorizeBadRequestException("invalid_request", "user is required");
    }
    if (Objects.isNull(authentication)) {
      throw new OAuthAuthorizeBadRequestException("invalid_request", "authentication is required");
    }
  }
}
