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

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.platform.json.JsonConverter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CredentialIssuerMetadataConfigurationTest {

  private static final String ISSUER = "https://idp.example.com/tenant";

  private final JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  private final Map<String, Object> stored =
      Map.of(
          "credential_endpoint",
          ISSUER + "/v1/credentials",
          "nonce_endpoint",
          ISSUER + "/v1/credentials/nonce",
          "display",
          List.of(Map.of("name", "Example Issuer", "locale", "en-US")),
          "credential_configurations_supported",
          Map.of(
              "identity_credential",
              Map.of(
                  "format", "dc+sd-jwt",
                  "scope", "identity_credential",
                  "vct", "urn:example:identity_credential",
                  "cryptographic_binding_methods_supported", List.of("jwk"),
                  "credential_signing_alg_values_supported", List.of("ES256"),
                  "proof_types_supported",
                      Map.of(
                          "jwt",
                          Map.of(
                              "proof_signing_alg_values_supported",
                              List.of("ES256"),
                              "key_attestations_required",
                              Map.of())),
                  "credential_metadata",
                      Map.of("claims", List.of(Map.of("path", List.of("given_name")))))));

  @Test
  @DisplayName("保存した形のまま往復する（管理 API の GET → PUT で失われない）")
  void storedFormRoundTrips() {
    CredentialIssuerMetadataConfiguration configuration =
        jsonConverter.read(stored, CredentialIssuerMetadataConfiguration.class);

    assertEquals(stored, configuration.toMap());
  }

  @Test
  @DisplayName("credential_issuer は未設定なら認可サーバーの issuer になる")
  void credentialIssuerFallsBackToTheAuthorizationServerIssuer() {
    CredentialIssuerMetadataConfiguration configuration =
        jsonConverter.read(stored, CredentialIssuerMetadataConfiguration.class);

    Map<String, Object> metadata = configuration.toMetadata(ISSUER);
    assertEquals(ISSUER, metadata.get("credential_issuer"));
    assertFalse(metadata.containsKey("authorization_servers"));
    assertFalse(configuration.toMap().containsKey("credential_issuer"));
  }

  @Test
  @DisplayName("key_attestations_required は空のオブジェクトでも「要求する」と読む")
  void emptyKeyAttestationsRequiredStillRequiresOne() {
    CredentialIssuerMetadataConfiguration configuration =
        jsonConverter.read(stored, CredentialIssuerMetadataConfiguration.class);

    CredentialConfiguration credential =
        configuration.credentialConfiguration("identity_credential");
    assertTrue(credential.proofType("jwt").requiresKeyAttestation());
    assertTrue(credential.requiresProof());
    assertTrue(credential.isSdJwtVc());
  }

  @Test
  @DisplayName("scope から credential configuration を引ける")
  void credentialConfigurationIsFoundByScope() {
    CredentialIssuerMetadataConfiguration configuration =
        jsonConverter.read(stored, CredentialIssuerMetadataConfiguration.class);

    CredentialConfiguration byScope =
        configuration.credentialConfigurationByScope("identity_credential");
    assertTrue(byScope.exists());
    assertEquals("identity_credential", configuration.credentialConfigurationIdOf(byScope));
    assertFalse(configuration.credentialConfigurationByScope("openid").exists());
  }

  @Test
  @DisplayName("旧ドラフト（credentials_supported の配列）の設定は Credential Issuer として扱わない")
  void draftShapedMetadataIsNotACredentialIssuer() {
    Map<String, Object> draft =
        Map.of(
            "credential_issuer",
            ISSUER,
            "credential_endpoint",
            ISSUER + "/v1/credentials",
            "batch_credential_endpoint",
            ISSUER + "/v1/batch-credentials",
            "credentials_supported",
            List.of(Map.of("format", "jwt_vc_json")));

    AuthorizationServerConfiguration authorizationServer =
        jsonConverter.read(
            Map.of("issuer", ISSUER, "credential_issuer_metadata", draft),
            AuthorizationServerConfiguration.class);

    assertFalse(authorizationServer.hasCredentialIssuerMetadata());
    assertFalse(authorizationServer.toMap().containsKey("credential_issuer_metadata"));
  }
}
