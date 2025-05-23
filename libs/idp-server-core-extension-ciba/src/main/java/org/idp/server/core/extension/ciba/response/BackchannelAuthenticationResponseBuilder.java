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

import java.util.HashMap;
import java.util.Map;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.basic.type.ciba.AuthReqId;
import org.idp.server.basic.type.ciba.Interval;
import org.idp.server.basic.type.oauth.ExpiresIn;

public class BackchannelAuthenticationResponseBuilder {

  AuthReqId authReqId;
  ExpiresIn expiresIn;
  Interval interval;
  Map<String, Object> values = new HashMap<>();
  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

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
    String response = jsonConverter.write(values);
    return new BackchannelAuthenticationResponse(authReqId, expiresIn, interval, response);
  }
}
