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

package org.idp.server.platform.jose;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class JwksGeneratorTest {

  @Test
  void generateRS256_returnsValidResult() {
    JwksGenerationResult result = JwksGenerator.generateRS256();

    assertNotNull(result);
    assertNotNull(result.jwksJson());
    assertNotNull(result.keyId());
    assertFalse(result.jwksJson().isEmpty());
    assertFalse(result.keyId().isEmpty());
  }

  @Test
  void generateRS256_canBeParsedByJwkParser() throws JsonWebKeyInvalidException {
    JwksGenerationResult result = JwksGenerator.generateRS256();

    JsonWebKeys keys = JwkParser.parseKeys(result.jwksJson());
    assertNotNull(keys);
  }

  @Test
  void generateRS256_containsPrivateKey() {
    JwksGenerationResult result = JwksGenerator.generateRS256();

    assertTrue(result.jwksJson().contains("\"d\""));
  }

  @Test
  void generateRS256_containsRS256AndSigAttributes() {
    JwksGenerationResult result = JwksGenerator.generateRS256();

    assertTrue(result.jwksJson().contains("\"RS256\""));
    assertTrue(result.jwksJson().contains("\"sig\""));
  }

  @Test
  void generateRS256_publicKeysCanBeExtracted() throws JsonWebKeyInvalidException {
    JwksGenerationResult result = JwksGenerator.generateRS256();

    Map<String, Object> publicKeys = JwkParser.parsePublicKeys(result.jwksJson());
    assertNotNull(publicKeys);
    assertFalse(publicKeys.isEmpty());
  }

  @Test
  void generateRS256_eachCallGeneratesDifferentKey() {
    JwksGenerationResult result1 = JwksGenerator.generateRS256();
    JwksGenerationResult result2 = JwksGenerator.generateRS256();

    assertNotEquals(result1.keyId(), result2.keyId());
    assertNotEquals(result1.jwksJson(), result2.jwksJson());
  }

  @Test
  void generateRS256_keyCanBeFoundByKid() throws JsonWebKeyInvalidException {
    JwksGenerationResult result = JwksGenerator.generateRS256();

    JsonWebKeys keys = JwkParser.parseKeys(result.jwksJson());
    JsonWebKey key = keys.findBy(result.keyId());
    assertNotNull(key);
    assertEquals(result.keyId(), key.keyId());
  }
}
