package org.idp.server.core.extension.ciba.response;

import org.idp.server.basic.type.ciba.AuthReqId;
import org.idp.server.basic.type.ciba.Interval;
import org.idp.server.basic.type.oauth.ExpiresIn;

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
