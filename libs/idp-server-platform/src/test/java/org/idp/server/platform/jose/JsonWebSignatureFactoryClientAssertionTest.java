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

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.SignedJWT;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonWebSignatureFactoryClientAssertionTest {

  static final String SECRET_32 = "0123456789abcdef0123456789abcdef";

  JsonWebSignatureFactory factory = new JsonWebSignatureFactory();

  @Test
  void signsWithASharedSecret() throws Exception {
    JsonWebSignature jws =
        factory.createWithSymmetricKey(Map.of("iss", "client"), Map.of(), "HS256", SECRET_32);

    SignedJWT parsed = SignedJWT.parse(jws.serialize());
    assertEquals(JWSAlgorithm.HS256, parsed.getHeader().getAlgorithm());
    assertTrue(parsed.verify(new MACVerifier(SECRET_32)));
  }

  @Test
  void refusesASecretShorterThanTheHash() {
    assertThrows(
        JoseInvalidException.class,
        () -> factory.createWithSymmetricKey(Map.of("iss", "client"), Map.of(), "HS256", "short"));
    assertThrows(
        JoseInvalidException.class,
        () ->
            factory.createWithSymmetricKey(Map.of("iss", "client"), Map.of(), "HS512", SECRET_32));
  }

  @Test
  void refusesAnAlgorithmThatIsNotHmacForASharedSecret() {
    assertThrows(
        JoseInvalidException.class,
        () ->
            factory.createWithSymmetricKey(Map.of("iss", "client"), Map.of(), "RS256", SECRET_32));
  }

  @Test
  void signsWithTheGivenAlgorithmRatherThanTheKeysOwn() throws Exception {
    RSAKey rsaKey = new RSAKeyGenerator(2048).keyID("k1").algorithm(JWSAlgorithm.RS256).generate();
    JsonWebKey jsonWebKey = JwkParser.parse(rsaKey.toJSONString());

    JsonWebSignature jws =
        factory.createWithAsymmetricKey(Map.of("iss", "client"), Map.of(), jsonWebKey, "PS256");

    SignedJWT parsed = SignedJWT.parse(jws.serialize());
    assertEquals(JWSAlgorithm.PS256, parsed.getHeader().getAlgorithm());
    assertEquals("k1", parsed.getHeader().getKeyID());
    assertTrue(parsed.verify(new RSASSAVerifier(rsaKey.toRSAPublicKey())));
  }

  @Test
  void refusesAnAlgorithmThatDoesNotSuitTheKey() throws Exception {
    RSAKey rsaKey = new RSAKeyGenerator(2048).keyID("k1").generate();
    JsonWebKey jsonWebKey = JwkParser.parse(rsaKey.toJSONString());

    assertThrows(
        JoseInvalidException.class,
        () ->
            factory.createWithAsymmetricKey(
                Map.of("iss", "client"), Map.of(), jsonWebKey, "ES256"));
  }
}
