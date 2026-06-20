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

package org.idp.server.core.extension.identity.verification.result;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.platform.json.JsonNodeWrapper;

/**
 * The user-facing claims a verification result actually applied to the user at approval (#1607).
 *
 * <p>"claims" is used in the broad OIDC sense — assertions about the user — so this single record
 * covers the standard {@code user_claims}, the {@code custom_properties}, and the {@code
 * user_status} the result transitioned the user to. Persisted as one JSON column ({@code
 * applied_user_claims}) so each result row is self-describing for audit; the stored shape is:
 *
 * <pre>{ "user_claims": {...}, "custom_properties": {...}, "user_status": "IDENTITY_VERIFIED" }
 * </pre>
 *
 * Parts that were not applied are omitted, so the record only carries what actually changed.
 */
public class AppliedUserClaims {

  JsonNodeWrapper json;

  public AppliedUserClaims() {
    this.json = JsonNodeWrapper.empty();
  }

  public AppliedUserClaims(JsonNodeWrapper json) {
    this.json = json;
  }

  public static AppliedUserClaims create(
      Map<String, Object> userClaims, Map<String, Object> customProperties, String userStatus) {
    Map<String, Object> shape = new HashMap<>();
    if (userClaims != null && !userClaims.isEmpty()) {
      shape.put("user_claims", userClaims);
    }
    if (customProperties != null && !customProperties.isEmpty()) {
      shape.put("custom_properties", customProperties);
    }
    if (userStatus != null && !userStatus.isEmpty()) {
      shape.put("user_status", userStatus);
    }
    return new AppliedUserClaims(JsonNodeWrapper.fromMap(shape));
  }

  public static AppliedUserClaims fromJson(String json) {
    return new AppliedUserClaims(JsonNodeWrapper.fromString(json));
  }

  public static AppliedUserClaims fromMap(Map<String, Object> json) {
    return new AppliedUserClaims(JsonNodeWrapper.fromMap(json));
  }

  public Map<String, Object> toMap() {
    return json.toMap();
  }

  public String toJson() {
    return json.toJson();
  }

  public boolean exists() {
    return json != null && json.exists();
  }
}
