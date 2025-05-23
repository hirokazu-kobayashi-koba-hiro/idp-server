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

package org.idp.server.authenticators.webauthn4j;

import java.util.HashMap;
import java.util.Map;

public class WebAuthn4jCredential {
  byte[] id;

  String userId;

  String rpId;

  byte[] publicKey;

  byte[] attestationObject;

  long signCount;

  public WebAuthn4jCredential() {}

  public WebAuthn4jCredential(
      byte[] id,
      String userId,
      String rpId,
      byte[] publicKey,
      byte[] attestationObject,
      long signCount) {
    this.id = id;
    this.userId = userId;
    this.rpId = rpId;
    this.publicKey = publicKey;
    this.attestationObject = attestationObject;
    this.signCount = signCount;
  }

  public byte[] id() {
    return id;
  }

  public String userId() {
    return userId;
  }

  public String rpId() {
    return rpId;
  }

  public byte[] publicKey() {
    return publicKey;
  }

  public long signCount() {
    return signCount;
  }

  public byte[] attestationObject() {
    return attestationObject;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new HashMap<>();
    result.put("id", id);
    result.put("user_id", userId);
    result.put("rp_id", rpId);
    result.put("public_key", publicKey);
    result.put("attestation_object", attestationObject);
    result.put("sign_count", signCount);
    return result;
  }
}
