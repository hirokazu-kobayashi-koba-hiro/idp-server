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

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.idp.server.core.openid.clientinstance.registration.ClientInstanceRegistrationChallenge;
import org.idp.server.core.openid.clientinstance.registration.ClientInstanceRegistrationException;
import org.idp.server.core.openid.clientinstance.registration.ClientInstanceRequestHash;
import org.idp.server.core.openid.clientinstance.registration.verifier.ClientInstanceRegistrationIdTokenVerifier;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.jose.JsonWebTokenClaims;
import org.idp.server.platform.json.JsonConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The ID token that authenticates a Client Instance registration.
 *
 * <p>What these tests hold is the binding: an ID token authenticates the registration of the key
 * its nonce was computed for, and of no other key, client or challenge.
 */
class ClientInstanceRegistrationIdTokenVerifierTest {

  static final JsonConverter JSON = JsonConverter.snakeCaseInstance();
  static final String ISSUER = "https://idp.example.com/tenant";
  static final String CLIENT_ID = "mobile-app";
  static final String REGISTRATION_CLIENT_ID = "mobile-app-bootstrap";
  static final String CHALLENGE = "Zm9vYmFyLWNoYWxsZW5nZS0wMQ";

  ClientInstanceRegistrationIdTokenVerifier verifier =
      new ClientInstanceRegistrationIdTokenVerifier();
  ECKey serverKey;
  AuthorizationServerConfiguration serverConfiguration;
  ClientConfiguration clientConfiguration;
  ClientInstanceRegistrationChallenge challenge;
  Map<String, Object> instanceKey;
  String subject;

  @BeforeEach
  void setUp() throws Exception {
    serverKey = new ECKeyGenerator(Curve.P_256).keyID("server-key").generate();
    serverConfiguration =
        JSON.read(
            JSON.write(Map.of("issuer", ISSUER, "jwks", new JWKSet(serverKey).toString(false))),
            AuthorizationServerConfiguration.class);
    clientConfiguration =
        JSON.read(
            JSON.write(
                Map.of(
                    "client_id",
                    CLIENT_ID,
                    "extension",
                    Map.of(
                        "client_instance_registration_clients", List.of(REGISTRATION_CLIENT_ID)))),
            ClientConfiguration.class);

    LocalDateTime issuedAt = SystemDateTime.now().minusSeconds(30);
    challenge =
        new ClientInstanceRegistrationChallenge(
            CHALLENGE,
            UUID.randomUUID().toString(),
            CLIENT_ID,
            "instance-0001",
            issuedAt.plusMinutes(5),
            null,
            issuedAt);
    instanceKey = new ECKeyGenerator(Curve.P_256).generate().toPublicJWK().toJSONObject();
    subject = UUID.randomUUID().toString();
  }

  private String nonceFor(Map<String, Object> key) {
    return ClientInstanceRequestHash.derive(CHALLENGE, key).value();
  }

  private JWTClaimsSet.Builder validClaims() {
    long now = System.currentTimeMillis();
    return new JWTClaimsSet.Builder()
        .issuer(ISSUER)
        .subject(subject)
        .audience(CLIENT_ID)
        .issueTime(new Date(now))
        .expirationTime(new Date(now + 300_000))
        .claim("nonce", nonceFor(instanceKey));
  }

  private String sign(JWTClaimsSet claims, ECKey key) throws Exception {
    SignedJWT jwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(key.getKeyID()).build(), claims);
    jwt.sign(new ECDSASigner(key));
    return jwt.serialize();
  }

  private String verify(String idToken) {
    JsonWebTokenClaims claims =
        verifier.verify(serverConfiguration, clientConfiguration, challenge, instanceKey, idToken);
    return claims.getSub();
  }

  private void assertRejected(String idToken, String expectedReason) {
    ClientInstanceRegistrationException exception =
        assertThrows(ClientInstanceRegistrationException.class, () -> verify(idToken));
    assertTrue(
        exception.getMessage().contains(expectedReason),
        () -> "expected [" + expectedReason + "] in [" + exception.getMessage() + "]");
  }

  @Test
  void acceptsTheClientsOwnIdTokenBoundToTheKey() throws Exception {
    assertEquals(subject, verify(sign(validClaims().build(), serverKey)));
  }

  @Test
  void acceptsAnIdTokenOfAListedRegistrationClient() throws Exception {
    String idToken = sign(validClaims().audience(REGISTRATION_CLIENT_ID).build(), serverKey);

    assertEquals(subject, verify(idToken));
  }

  @Test
  void rejectsAnIdTokenOfAClientThatIsNotListed() throws Exception {
    String idToken = sign(validClaims().audience("third-party-app").build(), serverKey);

    assertRejected(idToken, "audience");
  }

  @Test
  void rejectsAnIdTokenObtainedForAnotherKey() throws Exception {
    // A leaked ID token presented next to the attacker's own key: its nonce names the victim's.
    Map<String, Object> victimKey =
        new ECKeyGenerator(Curve.P_256).generate().toPublicJWK().toJSONObject();
    String idToken = sign(validClaims().claim("nonce", nonceFor(victimKey)).build(), serverKey);

    assertRejected(idToken, "nonce");
  }

  @Test
  void rejectsANonceThatCoversTheChallengeOnly() throws Exception {
    // The challenge alone would not tie the ID token to the key being registered.
    String idToken = sign(validClaims().claim("nonce", CHALLENGE).build(), serverKey);

    assertRejected(idToken, "nonce");
  }

  @Test
  void rejectsAnIdTokenWithoutNonce() throws Exception {
    String idToken = sign(validClaims().claim("nonce", null).build(), serverKey);

    assertRejected(idToken, "nonce");
  }

  @Test
  void rejectsAnIdTokenSignedByAnotherKey() throws Exception {
    ECKey otherKey = new ECKeyGenerator(Curve.P_256).keyID("server-key").generate();

    assertRejected(sign(validClaims().build(), otherKey), "id_token is invalid");
  }

  @Test
  void rejectsAnUnsignedIdToken() {
    String idToken = new PlainJWT(validClaims().build()).serialize();

    assertRejected(idToken, "signed JWT");
  }

  @Test
  void rejectsAnIdTokenOfAnotherIssuer() throws Exception {
    String idToken = sign(validClaims().issuer("https://other.example.com").build(), serverKey);

    assertRejected(idToken, "not issued by this authorization server");
  }

  @Test
  void rejectsAnExpiredIdToken() throws Exception {
    long now = System.currentTimeMillis();
    String idToken =
        sign(
            validClaims()
                .issueTime(new Date(now - 10_000))
                .expirationTime(new Date(now - 1_000))
                .build(),
            serverKey);

    assertRejected(idToken, "expired");
  }

  @Test
  void rejectsAnIdTokenIssuedBeforeTheChallenge() throws Exception {
    // A login from before the registration was started is not a login for this registration.
    long challengeIssuedAt = SystemDateTime.toEpochMilli(challenge.createdAt());
    String idToken =
        sign(validClaims().issueTime(new Date(challengeIssuedAt - 600_000)).build(), serverKey);

    assertRejected(idToken, "before the registration challenge");
  }

  @Test
  void rejectsAnIdTokenIssuedInTheFuture() throws Exception {
    String idToken =
        sign(
            validClaims().issueTime(new Date(System.currentTimeMillis() + 600_000)).build(),
            serverKey);

    assertRejected(idToken, "future");
  }

  @Test
  void rejectsAMissingIdToken() {
    assertRejected(null, "id_token is required");
  }
}
