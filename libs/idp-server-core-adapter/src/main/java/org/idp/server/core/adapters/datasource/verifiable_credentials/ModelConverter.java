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

package org.idp.server.core.adapters.datasource.verifiable_credentials;

import java.util.Map;
import org.idp.server.core.extension.verifiable_credentials.VerifiableCredentialTransaction;
import org.idp.server.core.extension.verifiable_credentials.VerifiableCredentialTransactionStatus;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.core.openid.oauth.type.oauth.Subject;
import org.idp.server.core.openid.oauth.type.vc.Credential;
import org.idp.server.core.openid.oauth.type.verifiablecredential.CredentialIssuer;
import org.idp.server.core.openid.oauth.type.verifiablecredential.TransactionId;
import org.idp.server.platform.json.JsonConverter;

class ModelConverter {

  private static final JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  static VerifiableCredentialTransaction convert(Map<String, String> stringMap) {
    TransactionId id = new TransactionId(stringMap.get("transaction_id"));
    CredentialIssuer credentialIssuer = new CredentialIssuer(stringMap.get("credential_issuer"));
    RequestedClientId requestedClientId = new RequestedClientId(stringMap.get("client_id"));
    Subject subject = new Subject(stringMap.get("user_id"));
    Credential credential = toVerifiableCredential(stringMap);
    VerifiableCredentialTransactionStatus status =
        VerifiableCredentialTransactionStatus.valueOf(stringMap.get("status"));
    return new VerifiableCredentialTransaction(
        id, credentialIssuer, requestedClientId, subject, credential, status);
  }

  private static Credential toVerifiableCredential(Map<String, String> stringMap) {
    String credential = stringMap.get("verifiable_credential");
    if (credential.isEmpty()) {
      return new Credential();
    }
    return new Credential(jsonConverter.read(credential, Map.class));
  }
}
