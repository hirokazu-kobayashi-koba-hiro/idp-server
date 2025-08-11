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

package org.idp.server.core.openid.authentication.interaction;

import java.util.HashMap;
import java.util.Map;

public class AuthenticationInteraction {
  String transactionId;
  String type;
  Map<String, Object> payload;

  public AuthenticationInteraction() {}

  public AuthenticationInteraction(String transactionId, String type, Map<String, Object> payload) {
    this.transactionId = transactionId;
    this.type = type;
    this.payload = payload;
  }

  public String transactionId() {
    return transactionId;
  }

  public String type() {
    return type;
  }

  public Map<String, Object> payload() {
    return payload;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("transaction_id", transactionId);
    map.put("type", type);
    map.put("payload", payload);
    return map;
  }

  public boolean exists() {
    return transactionId != null && !transactionId.isEmpty();
  }
}
