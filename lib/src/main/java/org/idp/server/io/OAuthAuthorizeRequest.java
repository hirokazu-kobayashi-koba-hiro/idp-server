package org.idp.server.io;

import java.util.Map;
import org.idp.server.core.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.core.type.TokenIssuer;

/** OAuthAuthorizeRequest */
public class OAuthAuthorizeRequest {
  String id;
  String tokenIssure;
  Map<String, Object> customProperties;
  // TODO user

  public OAuthAuthorizeRequest(String id, String tokenIssuer) {
    this.id = id;
    this.tokenIssure = tokenIssuer;
  }

  public OAuthAuthorizeRequest setCustomProperties(Map<String, Object> customProperties) {
    this.customProperties = customProperties;
    return this;
  }

  public AuthorizationRequestIdentifier toIdentifier() {
    return new AuthorizationRequestIdentifier(id);
  }

  public Map<String, Object> customProperties() {
    return customProperties;
  }

  public TokenIssuer toTokenIssuer() {
    return new TokenIssuer(tokenIssure);
  }
}
