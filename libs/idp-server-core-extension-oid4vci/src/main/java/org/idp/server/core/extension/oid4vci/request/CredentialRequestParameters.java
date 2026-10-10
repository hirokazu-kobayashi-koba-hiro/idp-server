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
package org.idp.server.core.extension.oid4vci.request;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The body of a Credential Request (OpenID4VCI 1.0 Section 8.2). Unrecognized parameters are
 * ignored, as the section requires.
 */
public class CredentialRequestParameters {

  Map<String, Object> values;

  public CredentialRequestParameters(Map<String, Object> values) {
    this.values = values != null ? values : new HashMap<>();
  }

  public boolean hasCredentialIdentifier() {
    return values.containsKey("credential_identifier");
  }

  public String credentialIdentifier() {
    Object value = values.get("credential_identifier");
    return value instanceof String string ? string : "";
  }

  public boolean hasCredentialConfigurationId() {
    return values.containsKey("credential_configuration_id");
  }

  public String credentialConfigurationId() {
    Object value = values.get("credential_configuration_id");
    return value instanceof String string ? string : "";
  }

  public boolean hasProofs() {
    return values.get("proofs") instanceof Map<?, ?>;
  }

  /** The proof types named in {@code proofs}; the section allows exactly one. */
  public List<String> proofTypes() {
    if (!(values.get("proofs") instanceof Map<?, ?> proofs)) {
      return List.of();
    }
    return proofs.keySet().stream().map(String::valueOf).toList();
  }

  /**
   * The proofs of the given type. A value that is not an array of strings yields the elements that
   * are strings, and the caller compares the count against what was sent.
   */
  public List<String> proofsOf(String proofType) {
    if (!(values.get("proofs") instanceof Map<?, ?> proofs)) {
      return List.of();
    }
    if (!(proofs.get(proofType) instanceof List<?> list)) {
      return List.of();
    }
    List<String> result = new ArrayList<>();
    for (Object element : list) {
      if (element instanceof String string) {
        result.add(string);
      }
    }
    return result;
  }

  public int proofCountOf(String proofType) {
    if (!(values.get("proofs") instanceof Map<?, ?> proofs)) {
      return 0;
    }
    return proofs.get(proofType) instanceof List<?> list ? list.size() : 0;
  }

  public boolean hasCredentialResponseEncryption() {
    return values.containsKey("credential_response_encryption");
  }
}
