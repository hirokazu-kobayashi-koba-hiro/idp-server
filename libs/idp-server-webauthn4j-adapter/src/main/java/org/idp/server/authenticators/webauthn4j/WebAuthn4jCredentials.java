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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.idp.server.authentication.interactors.fido2.Fido2CredentialNotFoundException;

public class WebAuthn4jCredentials implements Iterable<WebAuthn4jCredential> {

  List<WebAuthn4jCredential> values;

  public WebAuthn4jCredentials() {
    this.values = new ArrayList<>();
  }

  public WebAuthn4jCredentials(List<WebAuthn4jCredential> values) {
    this.values = values;
  }

  @Override
  public Iterator<WebAuthn4jCredential> iterator() {
    return values.iterator();
  }

  public WebAuthn4jCredential get(String rpId) {
    return values.stream()
        .filter(item -> item.rpId().equals(rpId))
        .findFirst()
        .orElseThrow(() -> new Fido2CredentialNotFoundException("No credential found for " + rpId));
  }

  /**
   * Converts credentials to WebAuthn allowCredentials format.
   *
   * <p>Generates an array of PublicKeyCredentialDescriptor objects for use in authentication
   * requests. Each descriptor contains the credential ID and transports.
   *
   * @return List of credential descriptors, or empty list if no credentials
   */
  public List<Map<String, Object>> toAllowCredentials() {
    return values.stream()
        .map(
            credential -> {
              Map<String, Object> descriptor = new java.util.HashMap<>();
              descriptor.put("type", "public-key");
              descriptor.put("id", credential.id());
              if (credential.transports() != null && !credential.transports().isEmpty()) {
                descriptor.put("transports", credential.transports());
              }
              return descriptor;
            })
        .collect(java.util.stream.Collectors.toList());
  }

  /**
   * Extracts credential IDs from credentials.
   *
   * <p>Returns the Base64URL-encoded credential IDs for use in allowCredentials validation during
   * authentication. This is used to verify that the credential ID in the authentication response
   * was in the original allowCredentials list (CVE-2025-26788 mitigation).
   *
   * @return List of credential IDs (Base64URL encoded), or empty list if no credentials
   */
  public List<String> toCredentialIds() {
    return values.stream()
        .map(WebAuthn4jCredential::id)
        .collect(java.util.stream.Collectors.toList());
  }
}
