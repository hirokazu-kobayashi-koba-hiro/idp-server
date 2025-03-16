package org.idp.server.core.handler.ciba.io;

import org.idp.server.core.type.ciba.AuthReqId;
import org.idp.server.core.type.oauth.TokenIssuer;

public class CibaDenyRequest {
  String authReqId;
  String tokenIssuer;

  public CibaDenyRequest(String authReqId, String tokenIssuer) {
    this.authReqId = authReqId;
    this.tokenIssuer = tokenIssuer;
  }

  public AuthReqId toAuthReqId() {
    return new AuthReqId(authReqId);
  }

  public TokenIssuer toTokenIssuer() {
    return new TokenIssuer(tokenIssuer);
  }
}
