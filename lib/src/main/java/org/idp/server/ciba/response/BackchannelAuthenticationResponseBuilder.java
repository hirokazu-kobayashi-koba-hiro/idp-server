package org.idp.server.ciba.response;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.basic.json.JsonParser;
import org.idp.server.type.ciba.AuthReqId;
import org.idp.server.type.ciba.Interval;
import org.idp.server.type.oauth.ExpiresIn;

public class BackchannelAuthenticationResponseBuilder {

  AuthReqId authReqId;
  ExpiresIn expiresIn;
  Interval interval;
  Map<String, Object> values = new HashMap<>();
  JsonParser jsonParser = JsonParser.createWithSnakeCaseStrategy();

  public BackchannelAuthenticationResponseBuilder() {}

  public BackchannelAuthenticationResponseBuilder add(AuthReqId authReqId) {
    this.authReqId = authReqId;
    this.values.put("auth_req_id", authReqId.value());
    return this;
  }

  public BackchannelAuthenticationResponseBuilder add(ExpiresIn expiresIn) {
    this.expiresIn = expiresIn;
    this.values.put("expires_in", expiresIn.value());
    return this;
  }

  public BackchannelAuthenticationResponseBuilder add(Interval interval) {
    this.interval = interval;
    this.values.put("interval", interval.value());
    return this;
  }

  public BackchannelAuthenticationResponse build() {
    String response = jsonParser.write(values);
    return new BackchannelAuthenticationResponse(authReqId, expiresIn, interval, response);
  }
}
