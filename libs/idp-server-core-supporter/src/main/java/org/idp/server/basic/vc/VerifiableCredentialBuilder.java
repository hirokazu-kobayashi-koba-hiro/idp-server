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

package org.idp.server.basic.vc;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VerifiableCredentialBuilder {
  Map<String, Object> values;

  public VerifiableCredentialBuilder() {
    this.values = new HashMap<>();
  }

  public VerifiableCredentialBuilder context(List<String> context) {
    values.put("@context", context);
    return this;
  }

  public VerifiableCredentialBuilder id(String id) {
    values.put("id", id);
    return this;
  }

  public VerifiableCredentialBuilder type(List<String> type) {
    values.put("type", type);
    return this;
  }

  public VerifiableCredentialBuilder issuer(String issuer) {
    values.put("issuer", issuer);
    return this;
  }

  public VerifiableCredentialBuilder issuanceDate(LocalDateTime issuanceDate) {
    values.put("issuanceDate", issuanceDate);
    return this;
  }

  public VerifiableCredentialBuilder credentialSubject(Map<String, Object> credentialSubject) {
    values.put("credentialSubject", credentialSubject);
    return this;
  }

  public VerifiableCredentialBuilder proof(Map<String, Object> proof) {
    values.put("proof", proof);
    return this;
  }

  public Credential build() {
    return new Credential(values);
  }
}
