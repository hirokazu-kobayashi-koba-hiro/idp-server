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

package org.idp.server.core.openid.clientinstance.registration;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;

/** Request of the challenge endpoint. */
public class ClientInstanceChallengeRequest {

  Map<String, Object> values;

  public ClientInstanceChallengeRequest(Map<String, Object> values) {
    this.values = values != null ? values : new HashMap<>();
  }

  public RequestedClientId requestedClientId() {
    return new RequestedClientId(stringValue("client_id"));
  }

  public String deviceId() {
    return stringValue("device_id");
  }

  private String stringValue(String key) {
    Object value = values.get(key);
    return value instanceof String stringValue && !stringValue.isEmpty() ? stringValue : null;
  }
}
