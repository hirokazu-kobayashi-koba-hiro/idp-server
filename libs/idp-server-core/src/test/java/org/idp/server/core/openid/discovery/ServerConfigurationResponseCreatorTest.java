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

package org.idp.server.core.openid.discovery;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.platform.json.JsonConverter;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Discovery (well-known/openid-configuration) output for the OIDC4IDA Section 8 metadata fields.
 *
 * <p>Covers issue #1513: the document/electronic-record fields use the OIDC4IDA 1.0 spec names
 * ({@code documents_supported}, {@code documents_methods_supported}, {@code
 * documents_check_methods_supported}, {@code electronic_records_supported}), the legacy names
 * ({@code id_documents_*}) are no longer emitted, and each field is advertised only when
 * configured.
 */
class ServerConfigurationResponseCreatorTest {

  private static final JsonConverter JSON = JsonConverter.snakeCaseInstance();

  private AuthorizationServerConfiguration config(String json) {
    return JSON.read(json, AuthorizationServerConfiguration.class);
  }

  private Map<String, Object> discovery(AuthorizationServerConfiguration config) {
    return new ServerConfigurationResponseCreator(config).create();
  }

  @Nested
  class Ida {

    @Test
    void emitsOidc4idaSpecFieldNames() {
      Map<String, Object> map =
          discovery(
              config(
                  """
                  {
                    "verified_claims_supported": true,
                    "trust_frameworks_supported": ["eidas"],
                    "evidence_supported": ["document", "electronic_record"],
                    "documents_supported": ["passport", "idcard"],
                    "documents_methods_supported": ["pipp"],
                    "electronic_records_supported": ["secure_messaging"],
                    "claims_in_verified_claims_supported": ["given_name"]
                  }
                  """));

      assertEquals(List.of("passport", "idcard"), map.get("documents_supported"));
      assertEquals(List.of("pipp"), map.get("documents_methods_supported"));
      assertEquals(List.of("secure_messaging"), map.get("electronic_records_supported"));
    }

    @Test
    void omitsLegacyFieldNames() {
      Map<String, Object> map =
          discovery(
              config(
                  """
                  {
                    "verified_claims_supported": true,
                    "documents_supported": ["passport"],
                    "documents_methods_supported": ["pipp"]
                  }
                  """));

      assertFalse(map.containsKey("id_documents_supported"));
      assertFalse(map.containsKey("id_documents_verification_methods_supported"));
    }

    @Test
    void omitsUnconfiguredOptionalFields() {
      Map<String, Object> map =
          discovery(
              config(
                  """
                  {
                    "verified_claims_supported": true,
                    "documents_supported": ["passport"]
                  }
                  """));

      // configured -> present
      assertTrue(map.containsKey("documents_supported"));
      // not configured -> absent (empty arrays are not advertised)
      assertFalse(map.containsKey("documents_methods_supported"));
      assertFalse(map.containsKey("documents_check_methods_supported"));
      assertFalse(map.containsKey("electronic_records_supported"));
    }

    @Test
    void omitsAllIdaFieldsWhenVerifiedClaimsDisabled() {
      Map<String, Object> map =
          discovery(
              config(
                  """
                  {
                    "verified_claims_supported": false,
                    "documents_supported": ["passport"]
                  }
                  """));

      assertEquals(false, map.get("verified_claims_supported"));
      assertFalse(map.containsKey("documents_supported"));
      assertFalse(map.containsKey("trust_frameworks_supported"));
    }

    @Test
    void legacyConfigKeyNoLongerMapsToField() {
      // Hard rename without @JsonAlias: a stored config using the old key is silently ignored
      // on read (Jackson ignores unknown properties), so the renamed field stays empty.
      AuthorizationServerConfiguration legacy =
          config(
              """
              {
                "verified_claims_supported": true,
                "id_documents_supported": ["passport"]
              }
              """);

      assertFalse(legacy.hasDocumentsSupported());
      assertTrue(legacy.documentsSupported().isEmpty());
    }
  }
}
