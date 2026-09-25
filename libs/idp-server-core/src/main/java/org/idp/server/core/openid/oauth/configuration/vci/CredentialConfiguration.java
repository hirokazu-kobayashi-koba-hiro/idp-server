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
import java.util.Objects;
import org.idp.server.platform.json.JsonReadable;

/**
 * One entry of {@code credential_configurations_supported} (OpenID4VCI 1.0 Section 12.2.4).
 *
 * <pre>{@code
 * "identity_credential": {
 *   "format": "dc+sd-jwt",
 *   "scope": "identity_credential",
 *   "vct": "urn:example:identity_credential",
 *   "cryptographic_binding_methods_supported": ["jwk"],
 *   "credential_signing_alg_values_supported": ["ES256"],
 *   "proof_types_supported": { "jwt": { "proof_signing_alg_values_supported": ["ES256"] } },
 *   "credential_metadata": { "display": [...], "claims": [...] }
 * }
 * }</pre>
 *
 * <p>Format-specific members other than {@code vct} and the display-only {@code
 * credential_metadata} are kept as given, so what the operator stores is what the metadata
 * publishes.
 */
public class CredentialConfiguration implements JsonReadable {

  String format;
  String scope;
  String vct;
  List<String> cryptographicBindingMethodsSupported = new ArrayList<>();
  List<String> credentialSigningAlgValuesSupported = new ArrayList<>();
  Map<String, CredentialProofTypeConfiguration> proofTypesSupported = new HashMap<>();
  Map<String, Object> credentialMetadata;

  public CredentialConfiguration() {}

  public String format() {
    return format;
  }

  public boolean isSdJwtVc() {
    return "dc+sd-jwt".equals(format);
  }

  public String scope() {
    return scope;
  }

  public boolean hasScope() {
    return scope != null && !scope.isEmpty();
  }

  public String vct() {
    return vct;
  }

  public List<String> cryptographicBindingMethodsSupported() {
    return cryptographicBindingMethodsSupported;
  }

  /** A credential bound to a key of the holder needs a proof of that key in the request. */
  public boolean requiresProof() {
    return !cryptographicBindingMethodsSupported.isEmpty();
  }

  public List<String> credentialSigningAlgValuesSupported() {
    return credentialSigningAlgValuesSupported;
  }

  public Map<String, CredentialProofTypeConfiguration> proofTypesSupported() {
    return proofTypesSupported;
  }

  public boolean supportsProofType(String proofType) {
    return proofTypesSupported.containsKey(proofType);
  }

  public CredentialProofTypeConfiguration proofType(String proofType) {
    return proofTypesSupported.get(proofType);
  }

  public boolean exists() {
    return Objects.nonNull(format) && !format.isEmpty();
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("format", format);
    if (hasScope()) map.put("scope", scope);
    if (vct != null) map.put("vct", vct);
    if (!cryptographicBindingMethodsSupported.isEmpty()) {
      map.put("cryptographic_binding_methods_supported", cryptographicBindingMethodsSupported);
    }
    if (!credentialSigningAlgValuesSupported.isEmpty()) {
      map.put("credential_signing_alg_values_supported", credentialSigningAlgValuesSupported);
    }
    if (!proofTypesSupported.isEmpty()) {
      Map<String, Object> proofTypes = new HashMap<>();
      proofTypesSupported.forEach((type, config) -> proofTypes.put(type, config.toMap()));
      map.put("proof_types_supported", proofTypes);
    }
    if (credentialMetadata != null) map.put("credential_metadata", credentialMetadata);
    return map;
  }
}
