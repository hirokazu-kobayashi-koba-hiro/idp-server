/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
