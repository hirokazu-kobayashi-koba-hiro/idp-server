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

package org.idp.server.authenticators.webauthn4j.datasource.credential;

import java.util.Map;
import org.idp.server.authenticators.webauthn4j.WebAuthn4jCredential;

class ModelConverter {

  static WebAuthn4jCredential convert(Map<String, Object> result) {

    byte[] id = (byte[]) result.get("id");
    String userId = (String) result.get("user_id");
    String rpId = (String) result.get("rp_id");
    byte[] publicKey = (byte[]) result.get("public_key");
    byte[] attestationObject = (byte[]) result.get("attestation_object");
    long signCount = (long) result.get("sign_count");

    return new WebAuthn4jCredential(id, userId, rpId, publicKey, attestationObject, signCount);
  }
}
