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

package org.idp.server.authentication.interactors.sms;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import org.idp.server.platform.json.JsonReadable;

public class SmslVerificationChallengeRequest implements Serializable, JsonReadable {

  String providerId;
  String phoneNumber;

  public SmslVerificationChallengeRequest() {}

  public SmslVerificationChallengeRequest(String providerId, String phoneNumber) {
    this.providerId = providerId;
    this.phoneNumber = phoneNumber;
  }

  public String providerId() {
    return providerId;
  }

  public String phoneNumber() {
    return phoneNumber;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("provider_id", providerId);
    map.put("phone_number", phoneNumber);
    return map;
  }
}
