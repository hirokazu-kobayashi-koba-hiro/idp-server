package org.idp.server.oauth.validator;

import java.util.Objects;
import org.idp.server.oauth.authentication.Authentication;
import org.idp.server.oauth.exception.OAuthBadRequestException;
import org.idp.server.oauth.identity.User;
import org.idp.server.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.type.extension.CustomProperties;
import org.idp.server.type.oauth.TokenIssuer;

public class OAuthAuthorizeRequestValidator {
  TokenIssuer tokenIssuer;
  AuthorizationRequestIdentifier authorizationRequestIdentifier;
  User user;
  Authentication authentication;
  CustomProperties customProperties;

  public OAuthAuthorizeRequestValidator(
      TokenIssuer tokenIssuer,
      AuthorizationRequestIdentifier authorizationRequestIdentifier,
      User user,
      Authentication authentication,
      CustomProperties customProperties) {
    this.tokenIssuer = tokenIssuer;
    this.authorizationRequestIdentifier = authorizationRequestIdentifier;
    this.user = user;
    this.authentication = authentication;
    this.customProperties = customProperties;
  }

  public void validate() {
    throwExceptionIfNotRequiredParameters();
  }

  void throwExceptionIfNotRequiredParameters() {
    if (Objects.isNull(tokenIssuer) || !tokenIssuer.exists()) {
      throw new OAuthBadRequestException("invalid_request", "tokenIssuer is required");
    }
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
