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

package org.idp.server.core.openid.oauth.configuration.vci;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.json.JsonReadable;

/**
 * One entry of {@code proof_types_supported} (OpenID4VCI 1.0 Section 12.2.4).
 *
 * <pre>{@code
 * "jwt": {
 *   "proof_signing_alg_values_supported": ["ES256"],
 *   "key_attestations_required": { "key_storage": ["iso_18045_high"] }
 * }
 * }</pre>
 *
 * <p>{@code key_attestations_required} is meaningful by its presence alone: an empty object means a
 * key attestation is required without constraining its levels.
 */
public class CredentialProofTypeConfiguration implements JsonReadable {

  List<String> proofSigningAlgValuesSupported = new ArrayList<>();
  Map<String, Object> keyAttestationsRequired;

  public CredentialProofTypeConfiguration() {}

  public List<String> proofSigningAlgValuesSupported() {
    return proofSigningAlgValuesSupported;
  }

  public boolean supportsSigningAlg(String alg) {
    return proofSigningAlgValuesSupported.contains(alg);
  }

  public boolean requiresKeyAttestation() {
    return keyAttestationsRequired != null;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("proof_signing_alg_values_supported", proofSigningAlgValuesSupported);
    if (requiresKeyAttestation()) {
      map.put("key_attestations_required", keyAttestationsRequired);
    }
    return map;
  }
}
