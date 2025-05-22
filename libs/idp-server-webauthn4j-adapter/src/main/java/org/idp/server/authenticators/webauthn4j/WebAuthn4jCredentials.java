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
import org.idp.server.authentication.interactors.webauthn.WebAuthnCredentialNotFoundException;

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
        .orElseThrow(
            () -> new WebAuthnCredentialNotFoundException("No credential found for " + rpId));
  }
}
