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
package org.idp.server.platform.jose.sdjwt;

import static org.junit.jupiter.api.Assertions.*;

import com.authlete.sd.Disclosure;
import com.authlete.sd.SDJWT;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.util.Base64;
import com.nimbusds.jwt.SignedJWT;
import java.math.BigInteger;
import java.security.KeyPair;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.idp.server.platform.jose.JsonWebKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SdJwtIssuerTest {

  ECKey signingKey;
  byte[] certificate;

  @BeforeEach
  void setUp() throws Exception {
    ECKey generated = new ECKeyGenerator(Curve.P_256).keyID("issuer-key").generate();
    KeyPair keyPair = generated.toKeyPair();
    X500Name name = new X500Name("CN=issuer");
    certificate =
        new JcaX509v3CertificateBuilder(
                name,
                BigInteger.ONE,
                new Date(),
                new Date(System.currentTimeMillis() + 86_400_000L),
                name,
                keyPair.getPublic())
            .build(new JcaContentSignerBuilder("SHA256withECDSA").build(keyPair.getPrivate()))
            .getEncoded();
    signingKey =
        new ECKey.Builder(generated)
            .algorithm(JWSAlgorithm.ES256)
            .x509CertChain(List.of(Base64.encode(certificate)))
            .build();
  }

  private SdJwt issue(boolean includeX5c) throws Exception {
    return new SdJwtIssuer()
        .issue(
            Map.of("iss", "https://issuer.example.com", "vct", "urn:example:id"),
            Map.of("given_name", "Taro", "address", Map.of("country", "JP")),
            "dc+sd-jwt",
            new JsonWebKey(signingKey),
            includeX5c);
  }

  @Test
  @DisplayName("Issuer-signed JWT と Disclosure を ~ でつなぎ、末尾も ~ で終わる（RFC 9901 Section 4）")
  void compactSerialization() throws Exception {
    SdJwt sdJwt = issue(true);

    assertTrue(sdJwt.value().startsWith(sdJwt.issuerSignedJwt() + "~"));
    assertTrue(sdJwt.value().endsWith("~"));
    assertEquals(2, sdJwt.disclosureCount());
  }

  @Test
  @DisplayName("開示できるクレームは平文に出ず、Disclosure のダイジェストが _sd に並ぶ")
  void disclosuresAreDigestedIntoSd() throws Exception {
    SDJWT parsed = SDJWT.parse(issue(true).value());
    Map<String, Object> payload =
        SignedJWT.parse(parsed.getCredentialJwt()).getPayload().toJSONObject();

    assertEquals("sha-256", payload.get("_sd_alg"));
    assertEquals("urn:example:id", payload.get("vct"));
    assertFalse(payload.containsKey("given_name"));
    assertFalse(payload.containsKey("address"));

    @SuppressWarnings("unchecked")
    Set<String> sd = Set.copyOf((List<String>) payload.get("_sd"));
    Set<String> digests =
        parsed.getDisclosures().stream().map(Disclosure::digest).collect(Collectors.toSet());
    assertEquals(digests, sd);

    Map<String, Object> disclosed =
        parsed.getDisclosures().stream()
            .collect(Collectors.toMap(Disclosure::getClaimName, Disclosure::getClaimValue));
    assertEquals("Taro", disclosed.get("given_name"));
    assertEquals(Map.of("country", "JP"), disclosed.get("address"));
  }

  @Test
  @DisplayName("typ と x5c はヘッダーの登録済みパラメータとして載り、署名は証明書の鍵で検証できる")
  void headerCarriesTypeAndCertificateChain() throws Exception {
    SignedJWT jwt = SignedJWT.parse(SDJWT.parse(issue(true).value()).getCredentialJwt());

    assertEquals("dc+sd-jwt", jwt.getHeader().getType().getType());
    assertEquals("issuer-key", jwt.getHeader().getKeyID());
    assertArrayEquals(certificate, jwt.getHeader().getX509CertChain().get(0).decode());
    assertTrue(jwt.verify(new ECDSAVerifier(signingKey.toPublicJWK())));
  }

  @Test
  @DisplayName("x5c を求めなければヘッダーに載せない")
  void certificateChainIsOptional() throws Exception {
    SignedJWT jwt = SignedJWT.parse(SDJWT.parse(issue(false).value()).getCredentialJwt());

    assertNull(jwt.getHeader().getX509CertChain());
  }

  @Test
  @DisplayName("Disclosure の salt は毎回変わる")
  void saltsAreFresh() throws Exception {
    String first = issue(true).value();
    String second = issue(true).value();

    assertNotEquals(first, second);
  }
}
