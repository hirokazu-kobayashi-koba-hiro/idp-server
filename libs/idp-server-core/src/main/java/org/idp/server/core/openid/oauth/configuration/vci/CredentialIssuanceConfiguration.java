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

import java.util.HashMap;
import java.util.Map;
import org.idp.server.platform.json.JsonReadable;

/**
 * How the tenant builds the credentials it issues, held on the authorization server configuration
 * as {@code credential_issuance}. Nothing here is published: the metadata is {@link
 * CredentialIssuerMetadataConfiguration}.
 *
 * <pre>{@code
 * "credential_issuance": {
 *   "signing_key_id": "credential-signing-key",
 *   "credentials": {
 *     "identity_credential": {
 *       "expires_in": 31536000,
 *       "claims": [ { "name": "given_name", "from": "$.given_name" } ]
 *     }
 *   }
 * }
 * }</pre>
 *
 * <p>The signing key is a key of the tenant's {@code jwks}. When it carries a certificate chain
 * ({@code x5c}) the chain goes into the credential's header, which HAIP requires.
 */
public class CredentialIssuanceConfiguration implements JsonReadable {

  String signingKeyId;
  Map<String, CredentialIssuanceDefinition> credentials = new HashMap<>();

  public CredentialIssuanceConfiguration() {}

  public String signingKeyId() {
    return signingKeyId;
  }

  public boolean hasSigningKeyId() {
    return signingKeyId != null && !signingKeyId.isEmpty();
  }

  public CredentialIssuanceDefinition definition(String credentialConfigurationId) {
    CredentialIssuanceDefinition definition =
        credentials == null ? null : credentials.get(credentialConfigurationId);
    if (definition == null) {
      return new CredentialIssuanceDefinition();
    }
    return definition;
  }

  public boolean exists() {
    return hasSigningKeyId();
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("signing_key_id", signingKeyId);
    Map<String, Object> definitions = new HashMap<>();
    if (credentials != null) {
      credentials.forEach((id, definition) -> definitions.put(id, definition.toMap()));
    }
    map.put("credentials", definitions);
    return map;
  }
}
