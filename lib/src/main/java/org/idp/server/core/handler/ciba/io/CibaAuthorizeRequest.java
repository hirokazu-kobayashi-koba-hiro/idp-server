package org.idp.server.core.handler.ciba.io;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.type.ciba.AuthReqId;
import org.idp.server.core.type.extension.CustomProperties;
import org.idp.server.core.type.oauth.TokenIssuer;

public class CibaAuthorizeRequest {
  String authReqId;
  String tokenIssuer;
  // TODO authentication
  Map<String, Object> customProperties = new HashMap<>();

  public CibaAuthorizeRequest(String authReqId, String tokenIssuer) {
    this.authReqId = authReqId;
    this.tokenIssuer = tokenIssuer;
  }

  public CibaAuthorizeRequest setCustomProperties(Map<String, Object> customProperties) {
    this.customProperties = customProperties;
    return this;
  }

  public AuthReqId toAuthReqId() {
    return new AuthReqId(authReqId);
  }

  public CustomProperties toCustomProperties() {
    return new CustomProperties(customProperties);
  }

  public TokenIssuer toTokenIssuer() {
    return new TokenIssuer(tokenIssuer);
  }
}
