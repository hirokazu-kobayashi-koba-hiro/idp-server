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
package org.idp.server.platform.oauth.client;

import static org.junit.jupiter.api.Assertions.*;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.idp.server.platform.exception.InvalidConfigurationException;
import org.idp.server.platform.json.JsonConverter;
import org.junit.jupiter.api.Test;

class OutboundClientAuthenticatorsTest {

  static final String CLIENT_ID = "client-1";
  static final String SECRET = "0123456789abcdef0123456789abcdef";
  static final String TOKEN_ENDPOINT = "https://idp.example.com/token";
  static final String ISSUER = "https://idp.example.com";

  OutboundClientAuthenticators authenticators = new OutboundClientAuthenticators();

  static OutboundClientAuthenticationConfig config(Map<String, Object> values) {
    return JsonConverter.snakeCaseInstance().read(values, OutboundClientAuthenticationConfig.class);
  }

  static OutboundClientCredentials credentials(
      OutboundClientAuthenticationType type, String issuer, Map<String, Object> config) {
    return new OutboundClientCredentials(
        type, CLIENT_ID, SECRET, TOKEN_ENDPOINT, issuer, config(config));
  }

  static SignedJWT assertionOf(OutboundClientAuthentication authentication) throws Exception {
    Map<String, String> form = authentication.formParameters();
    assertEquals(CLIENT_ID, form.get("client_id"));
    assertEquals(ClientAssertionClaims.ASSERTION_TYPE, form.get("client_assertion_type"));
    assertFalse(form.containsKey("client_secret"));
    assertTrue(authentication.headers().isEmpty());
    return SignedJWT.parse(form.get("client_assertion"));
  }

  @Test
  void clientSecretBasicPutsTheSecretInTheAuthorizationHeader() {
    OutboundClientAuthentication authentication =
        authenticators.authenticate(
            credentials(OutboundClientAuthenticationType.client_secret_basic, null, Map.of()));

    String expected =
        "Basic "
            + Base64.getEncoder()
                .encodeToString((CLIENT_ID + ":" + SECRET).getBytes(StandardCharsets.UTF_8));
    assertEquals(Map.of("Authorization", expected), authentication.headers());
    assertEquals(Map.of("client_id", CLIENT_ID), authentication.formParameters());
  }

  @Test
  void clientSecretPostPutsTheSecretInTheForm() {
    OutboundClientAuthentication authentication =
        authenticators.authenticate(
            credentials(OutboundClientAuthenticationType.client_secret_post, null, Map.of()));

    assertTrue(authentication.headers().isEmpty());
    assertEquals(
        Map.of("client_id", CLIENT_ID, "client_secret", SECRET), authentication.formParameters());
  }

  @Test
  void noneSendsOnlyTheClientId() {
    OutboundClientAuthentication authentication =
        authenticators.authenticate(
            credentials(OutboundClientAuthenticationType.none, null, Map.of()));

    assertTrue(authentication.headers().isEmpty());
    assertEquals(Map.of("client_id", CLIENT_ID), authentication.formParameters());
  }

  @Test
  void clientSecretJwtSignsAnAssertionToTheTokenEndpointWithTheSecret() throws Exception {
    SignedJWT assertion =
        assertionOf(
            authenticators.authenticate(
                credentials(OutboundClientAuthenticationType.client_secret_jwt, ISSUER, Map.of())));

    assertEquals(JWSAlgorithm.HS256, assertion.getHeader().getAlgorithm());
    assertTrue(assertion.verify(new MACVerifier(SECRET)));
    JWTClaimsSet claims = assertion.getJWTClaimsSet();
    assertEquals(CLIENT_ID, claims.getIssuer());
    assertEquals(CLIENT_ID, claims.getSubject());
    assertEquals(java.util.List.of(TOKEN_ENDPOINT), claims.getAudience());
    assertNotNull(claims.getJWTID());
    assertEquals(
        60, (claims.getExpirationTime().getTime() - claims.getIssueTime().getTime()) / 1000);
  }

  @Test
  void eachAssertionCarriesAFreshJti() throws Exception {
    OutboundClientCredentials credentials =
        credentials(OutboundClientAuthenticationType.client_secret_jwt, null, Map.of());

    String first =
        assertionOf(authenticators.authenticate(credentials)).getJWTClaimsSet().getJWTID();
    String second =
        assertionOf(authenticators.authenticate(credentials)).getJWTClaimsSet().getJWTID();

    assertNotEquals(first, second);
  }

  @Test
  void privateKeyJwtSignsWithTheConfiguredKeyAndAlgorithm() throws Exception {
    ECKey key = new ECKeyGenerator(Curve.P_256).keyID("signing-1").generate();
    Map<String, Object> signingKey = key.toJSONObject();

    SignedJWT assertion =
        assertionOf(
            authenticators.authenticate(
                credentials(
                    OutboundClientAuthenticationType.private_key_jwt,
                    ISSUER,
                    Map.of("signing_algorithm", "ES256", "signing_key", signingKey))));

    assertEquals(JWSAlgorithm.ES256, assertion.getHeader().getAlgorithm());
    assertEquals("signing-1", assertion.getHeader().getKeyID());
    assertTrue(assertion.verify(new ECDSAVerifier(key.toECPublicKey())));
    assertEquals(java.util.List.of(TOKEN_ENDPOINT), assertion.getJWTClaimsSet().getAudience());
  }

  @Test
  void privateKeyJwtTakesTheKeysOwnAlgorithmWhenNoneIsConfigured() throws Exception {
    ECKey key =
        new ECKeyGenerator(Curve.P_256).keyID("signing-1").algorithm(JWSAlgorithm.ES256).generate();

    SignedJWT assertion =
        assertionOf(
            authenticators.authenticate(
                credentials(
                    OutboundClientAuthenticationType.private_key_jwt,
                    null,
                    Map.of("signing_key", key.toJSONObject()))));

    assertEquals(JWSAlgorithm.ES256, assertion.getHeader().getAlgorithm());
  }

  @Test
  void addressesTheAssertionToTheIssuerWhenAskedTo() throws Exception {
    SignedJWT assertion =
        assertionOf(
            authenticators.authenticate(
                credentials(
                    OutboundClientAuthenticationType.client_secret_jwt,
                    ISSUER,
                    Map.of("assertion_audience", "issuer"))));

    assertEquals(java.util.List.of(ISSUER), assertion.getJWTClaimsSet().getAudience());
  }

  @Test
  void takesTheIssuerFromTheConfigurationWhenTheCallerKnowsNone() throws Exception {
    SignedJWT assertion =
        assertionOf(
            authenticators.authenticate(
                credentials(
                    OutboundClientAuthenticationType.client_secret_jwt,
                    null,
                    Map.of("assertion_audience", "issuer", "issuer", ISSUER))));

    assertEquals(java.util.List.of(ISSUER), assertion.getJWTClaimsSet().getAudience());
  }

  @Test
  void refusesAnAssertionToTheIssuerWhenNoIssuerIsKnown() {
    assertThrows(
        InvalidConfigurationException.class,
        () ->
            authenticators.authenticate(
                credentials(
                    OutboundClientAuthenticationType.client_secret_jwt,
                    null,
                    Map.of("assertion_audience", "issuer"))));
  }

  @Test
  void privateKeyJwtRefusesAMissingOrPublicKey() throws Exception {
    assertThrows(
        InvalidConfigurationException.class,
        () ->
            authenticators.authenticate(
                credentials(OutboundClientAuthenticationType.private_key_jwt, null, Map.of())));

    ECKey key = new ECKeyGenerator(Curve.P_256).keyID("signing-1").generate();
    assertThrows(
        InvalidConfigurationException.class,
        () ->
            authenticators.authenticate(
                credentials(
                    OutboundClientAuthenticationType.private_key_jwt,
                    null,
                    Map.of(
                        "signing_algorithm",
                        "ES256",
                        "signing_key",
                        key.toPublicJWK().toJSONObject()))));
  }

  @Test
  void clientSecretJwtRefusesASecretTooShortForTheAlgorithm() {
    OutboundClientCredentials credentials =
        new OutboundClientCredentials(
            OutboundClientAuthenticationType.client_secret_jwt,
            CLIENT_ID,
            "short",
            TOKEN_ENDPOINT,
            null,
            null);

    assertThrows(
        InvalidConfigurationException.class, () -> authenticators.authenticate(credentials));
  }
}
