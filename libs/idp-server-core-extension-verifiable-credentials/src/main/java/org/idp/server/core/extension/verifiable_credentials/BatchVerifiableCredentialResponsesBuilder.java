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


package org.idp.server.core.extension.verifiable_credentials;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.basic.type.verifiablecredential.CNonce;
import org.idp.server.basic.type.verifiablecredential.CNonceExpiresIn;

public class BatchVerifiableCredentialResponsesBuilder {
  List<BatchVerifiableCredentialResponse> responses;
  CNonce cNonce;
  CNonceExpiresIn cNonceExpiresIn;
  Map<String, Object> values = new HashMap<>();
  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  public BatchVerifiableCredentialResponsesBuilder() {}

  public BatchVerifiableCredentialResponsesBuilder add(
      List<BatchVerifiableCredentialResponse> responses) {
    this.responses = responses;
    values.put("credential_responses", toResponses(responses));
    return this;
  }

  private List<Map<String, Object>> toResponses(List<BatchVerifiableCredentialResponse> responses) {
    return responses.stream().map(BatchVerifiableCredentialResponse::toMap).toList();
  }

  public BatchVerifiableCredentialResponsesBuilder add(CNonce cNonce) {
    this.cNonce = cNonce;
    values.put("c_nonce", cNonce.value());
    return this;
  }

  public BatchVerifiableCredentialResponsesBuilder add(CNonceExpiresIn cNonceExpiresIn) {
    this.cNonceExpiresIn = cNonceExpiresIn;
    values.put("c_nonce_expires_in", cNonceExpiresIn.value());
    return this;
  }

  public BatchVerifiableCredentialResponses build() {
    String contents = jsonConverter.write(values);
    return new BatchVerifiableCredentialResponses(responses, cNonce, cNonceExpiresIn, contents);
  }
}
