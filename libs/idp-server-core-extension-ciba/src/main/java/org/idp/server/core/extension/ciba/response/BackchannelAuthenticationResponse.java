/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
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
