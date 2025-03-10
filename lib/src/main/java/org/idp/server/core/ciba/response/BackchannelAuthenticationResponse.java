package org.idp.server.core.ciba.response;

import org.idp.server.core.type.ciba.AuthReqId;
import org.idp.server.core.type.ciba.Interval;
import org.idp.server.core.type.oauth.ExpiresIn;

public class BackchannelAuthenticationResponse {

  AuthReqId authReqId;
  ExpiresIn expiresIn;
  Interval interval;
  String contents;

  public BackchannelAuthenticationResponse() {}

  public BackchannelAuthenticationResponse(
      AuthReqId authReqId, ExpiresIn expiresIn, Interval interval, String contents) {
    this.authReqId = authReqId;
    this.expiresIn = expiresIn;
    this.interval = interval;
    this.contents = contents;
  }

  public AuthReqId authReqId() {
    return authReqId;
  }

  public ExpiresIn expiresIn() {
    return expiresIn;
  }

  public Interval interval() {
    return interval;
  }

  public String contents() {
    return contents;
  }
}
