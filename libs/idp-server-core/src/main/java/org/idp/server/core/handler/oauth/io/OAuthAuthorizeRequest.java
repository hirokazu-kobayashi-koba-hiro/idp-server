package org.idp.server.core.handler.oauth.io;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.oauth.authentication.Authentication;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.core.type.extension.CustomProperties;
import org.idp.server.core.type.oauth.TokenIssuer;

/** OAuthAuthorizeRequest */
public class OAuthAuthorizeRequest {
  String id;
  String tokenIssuer;
  User user;
  Authentication authentication;
  Map<String, Object> customProperties = new HashMap<>();

  public OAuthAuthorizeRequest(
      String id, String tokenIssuer, User user, Authentication authentication) {
    this.id = id;
    this.tokenIssuer = tokenIssuer;
    this.user = user;
    this.authentication = authentication;
  }

  public OAuthAuthorizeRequest setCustomProperties(Map<String, Object> customProperties) {
    this.customProperties = customProperties;
    return this;
  }

  public AuthorizationRequestIdentifier toIdentifier() {
    return new AuthorizationRequestIdentifier(id);
  }

  public User user() {
    return user;
  }

  public Authentication authentication() {
    return authentication;
  }

  public CustomProperties toCustomProperties() {
    return new CustomProperties(customProperties);
  }

  public TokenIssuer toTokenIssuer() {
    return new TokenIssuer(tokenIssuer);
  }
}
