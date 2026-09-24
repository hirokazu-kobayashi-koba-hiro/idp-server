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
package org.idp.server.core.openid.extension.attestation;

import org.idp.server.core.openid.clientinstance.ClientInstance;
import org.idp.server.platform.jose.JsonWebKey;

/**
 * What a verified Client Attestation JWT establishes: the Client Instance Key it binds via {@code
 * cnf.jwk}, and the registered Client Instance when the attestation was self-signed by one.
 */
public class VerifiedClientAttestation {

  JsonWebKey clientInstanceKey;
  ClientInstance clientInstance;

  public VerifiedClientAttestation(JsonWebKey clientInstanceKey, ClientInstance clientInstance) {
    this.clientInstanceKey = clientInstanceKey;
    this.clientInstance = clientInstance;
  }

  public JsonWebKey clientInstanceKey() {
    return clientInstanceKey;
  }

  public ClientInstance clientInstance() {
    return clientInstance;
  }
}
