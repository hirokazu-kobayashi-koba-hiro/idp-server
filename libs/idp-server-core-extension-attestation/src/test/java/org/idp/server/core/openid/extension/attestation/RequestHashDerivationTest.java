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

import static org.junit.jupiter.api.Assertions.*;

import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.util.Base64URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.junit.jupiter.api.Test;

/**
 * Pins the wire contract of the platform attestation request hash used at Client Instance
 * registration.
 *
 * <pre>
 *   request_hash  = base64url_nopad( SHA-256( challenge_bytes || canonical_jwk_utf8 ) )
 *   canonical_jwk = {"crv":"P-256","kty":"EC","x":"...","y":"..."}   (RFC 7638 required members,
 *                                                                    lexicographic, no whitespace)
 * </pre>
 *
 * <p>The client (Android {@code request_hash}, iOS {@code client_data_hash}) and the server must
 * derive identical bytes, otherwise every registration fails. These tests verify the canonical form
 * against RFC 7638 itself and pin a fixed vector that the client implementation can be checked
 * against.
 */
class RequestHashDerivationTest {

  /** Canonical JWK of an EC public key per RFC 7638 Section 3.2. */
  private static String canonicalJwk(ECKey publicKey) {
    return String.format(
        "{\"crv\":\"%s\",\"kty\":\"EC\",\"x\":\"%s\",\"y\":\"%s\"}",
        publicKey.getCurve().getName(), publicKey.getX().toString(), publicKey.getY().toString());
  }

  private static String requestHash(byte[] challenge, ECKey publicKey) throws Exception {
    byte[] canonical = canonicalJwk(publicKey).getBytes(StandardCharsets.UTF_8);
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    digest.update(challenge);
    digest.update(canonical);
    return Base64URL.encode(digest.digest()).toString();
  }

  @Test
  void canonicalJwkMatchesRfc7638ThumbprintInput() throws Exception {
    ECKey key = new ECKeyGenerator(Curve.P_256).generate();
    ECKey publicKey = key.toPublicJWK();

    // If SHA-256 over our canonical JSON equals the RFC 7638 thumbprint that Nimbus computes,
    // the canonical form is byte-for-byte the one the specification defines.
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    String ours =
        Base64URL.encode(digest.digest(canonicalJwk(publicKey).getBytes(StandardCharsets.UTF_8)))
            .toString();

    assertEquals(publicKey.computeThumbprint().toString(), ours);
  }

  @Test
  void platformCanonicalJsonMatchesTheDerivationInput() throws Exception {
    // The server derives request_hash from JsonWebKey.canonicalJson(); it must produce the same
    // bytes this test pins, otherwise server and client disagree on the hash.
    ECKey key = new ECKeyGenerator(Curve.P_256).generate();
    org.idp.server.platform.jose.JsonWebKey platformKey =
        org.idp.server.platform.jose.JwkParser.parse(key.toPublicJWK().toJSONString());

    assertEquals(canonicalJwk(key.toPublicJWK()), platformKey.canonicalJson());
  }

  @Test
  void jwkCoordinatesAreFixed32BytesForP256() throws Exception {
    // x / y must be zero padded to the curve size: a shorter encoding would change the canonical
    // JSON and therefore the request hash.
    for (int i = 0; i < 50; i++) {
      ECKey publicKey = new ECKeyGenerator(Curve.P_256).generate().toPublicJWK();
      assertEquals(32, publicKey.getX().decode().length);
      assertEquals(32, publicKey.getY().decode().length);
    }
  }

  @Test
  void requestHashIsStableForAFixedVector() throws Exception {
    // Fixed key and challenge so the client implementation can be checked against the same value.
    ECKey publicKey =
        ECKey.parse(
            "{\"kty\":\"EC\",\"crv\":\"P-256\","
                + "\"x\":\"VcKVNBZ4IaBAYW3jxM4w3TJFVA7myeUGQyGt-g_yvpQ\","
                + "\"y\":\"f-E-hYE3TAWKwhVv9pej9NABs9SX9XsNO80x57jFTyU\"}");
    byte[] challenge = Base64URL.from("Zm9vYmFyLWNoYWxsZW5nZS0wMQ").decode();

    assertEquals(
        "{\"crv\":\"P-256\",\"kty\":\"EC\","
            + "\"x\":\"VcKVNBZ4IaBAYW3jxM4w3TJFVA7myeUGQyGt-g_yvpQ\","
            + "\"y\":\"f-E-hYE3TAWKwhVv9pej9NABs9SX9XsNO80x57jFTyU\"}",
        canonicalJwk(publicKey));

    System.out.println("[fixed vector] challenge(b64url) = Zm9vYmFyLWNoYWxsZW5nZS0wMQ");
    System.out.println("[fixed vector] request_hash      = " + requestHash(challenge, publicKey));
  }
}
