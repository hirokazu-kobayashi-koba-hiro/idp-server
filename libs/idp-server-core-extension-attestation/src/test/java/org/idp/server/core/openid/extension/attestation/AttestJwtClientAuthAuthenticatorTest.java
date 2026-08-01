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

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import org.idp.server.core.openid.oauth.clientattestation.ClientAttestationJwt;
import org.idp.server.core.openid.oauth.clientattestation.ClientAttestationPopJwt;
import org.idp.server.core.openid.oauth.clientauthenticator.clientcredentials.ClientCredentials;
import org.idp.server.core.openid.oauth.clientauthenticator.exception.ClientUnAuthorizedException;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.type.oauth.ClientAuthenticationType;
import org.idp.server.core.openid.oauth.type.oauth.ClientSecretBasic;
import org.idp.server.core.openid.token.TokenRequestContext;
import org.idp.server.core.openid.token.TokenRequestParameters;
import org.idp.server.platform.json.JsonConverter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Verifies {@code attest_jwt_client_auth} per draft-ietf-oauth-attestation-based-client-auth-10
 * Section 7 (verification rules for the Client Attestation JWT and the Client Attestation PoP JWT).
 */
class AttestJwtClientAuthAuthenticatorTest {

  static final JsonConverter JSON = JsonConverter.snakeCaseInstance();
  static final String CLIENT_ID = "attested-client";
  static final String ISSUER = "https://idp.example.com/tenant-1";
  static final String ATTESTATION_TYP = "oauth-client-attestation+jwt";
  static final String POP_TYP = "oauth-client-attestation-pop+jwt";

  static ECKey attesterKey;
  static ECKey instanceKey;
  static ClientConfiguration clientConfiguration;
  static AuthorizationServerConfiguration serverConfiguration;

  AttestJwtClientAuthAuthenticator authenticator = new AttestJwtClientAuthAuthenticator();

  @BeforeAll
  static void setup() throws Exception {
    attesterKey =
        new ECKeyGenerator(Curve.P_256)
            .keyID("attester-1")
            .algorithm(JWSAlgorithm.ES256)
            .generate();
    instanceKey =
        new ECKeyGenerator(Curve.P_256)
            .keyID("instance-1")
            .algorithm(JWSAlgorithm.ES256)
            .generate();

    String attesterPublicJwks = new JWKSet(attesterKey.toPublicJWK()).toString();
    Map<String, Object> clientConfigMap =
        Map.of(
            "client_id", CLIENT_ID,
            "token_endpoint_auth_method", "attest_jwt_client_auth",
            "client_attestation_jwks", attesterPublicJwks);
    clientConfiguration = JSON.read(JSON.write(clientConfigMap), ClientConfiguration.class);
    serverConfiguration =
        JSON.read("{\"issuer\":\"" + ISSUER + "\"}", AuthorizationServerConfiguration.class);
  }

  private static String attestationJwt(String typ, String sub, Date exp, Object cnf)
      throws Exception {
    JWSHeader.Builder header = new JWSHeader.Builder(JWSAlgorithm.ES256).keyID("attester-1");
    if (typ != null) {
      header.type(new JOSEObjectType(typ));
    }
    JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder().subject(sub).expirationTime(exp);
    if (cnf != null) {
      claims.claim("cnf", cnf);
    }
    SignedJWT jwt = new SignedJWT(header.build(), claims.build());
    jwt.sign(new ECDSASigner(attesterKey));
    return jwt.serialize();
  }

  private static String validAttestationJwt() throws Exception {
    return attestationJwt(
        ATTESTATION_TYP,
        CLIENT_ID,
        new Date(System.currentTimeMillis() + 300_000),
        Map.of("jwk", instanceKey.toPublicJWK().toJSONObject()));
  }

  private static String popJwt(String typ, String aud, String jti, Date iat, ECKey signingKey)
      throws Exception {
    JWSHeader.Builder header = new JWSHeader.Builder(JWSAlgorithm.ES256);
    if (typ != null) {
      header.type(new JOSEObjectType(typ));
    }
    JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder();
    if (aud != null) {
      claims.audience(aud);
    }
    claims.jwtID(jti).issueTime(iat);
    SignedJWT jwt = new SignedJWT(header.build(), claims.build());
    jwt.sign(new ECDSASigner(signingKey));
    return jwt.serialize();
  }

  private static String validPopJwt() throws Exception {
    return popJwt(POP_TYP, ISSUER, UUID.randomUUID().toString(), new Date(), instanceKey);
  }

  private static TokenRequestContext contextWith(String attestationJwt, String popJwt) {
    TokenRequestParameters parameters =
        new TokenRequestParameters(Map.of("client_id", new String[] {CLIENT_ID}));
    return new TokenRequestContext(
        null,
        new ClientSecretBasic(),
        null,
        null,
        new ClientAttestationJwt(attestationJwt),
        new ClientAttestationPopJwt(popJwt),
        null,
        null,
        parameters,
        null,
        null,
        null,
        null,
        serverConfiguration,
        clientConfiguration);
  }

  @Test
  void authenticateSucceedsWithValidAttestationAndPop() throws Exception {
    ClientCredentials credentials =
        authenticator.authenticate(contextWith(validAttestationJwt(), validPopJwt()));

    assertEquals(
        ClientAuthenticationType.attest_jwt_client_auth, credentials.clientAuthenticationType());
    assertEquals(CLIENT_ID, credentials.clientId().value());
  }

  @Test
  void throwsWhenAttestationHeaderIsMissing() throws Exception {
    ClientUnAuthorizedException exception =
        assertThrows(
            ClientUnAuthorizedException.class,
            () -> authenticator.authenticate(contextWith(null, validPopJwt())));
    assertTrue(exception.getReason().contains("OAuth-Client-Attestation"));
  }

  @Test
  void throwsWhenPopHeaderIsMissing() throws Exception {
    ClientUnAuthorizedException exception =
        assertThrows(
            ClientUnAuthorizedException.class,
            () -> authenticator.authenticate(contextWith(validAttestationJwt(), null)));
    assertTrue(exception.getReason().contains("OAuth-Client-Attestation-PoP"));
  }

  @Test
  void throwsWhenAttestationTypIsInvalid() throws Exception {
    String attestation =
        attestationJwt(
            "JWT",
            CLIENT_ID,
            new Date(System.currentTimeMillis() + 300_000),
            Map.of("jwk", instanceKey.toPublicJWK().toJSONObject()));
    ClientUnAuthorizedException exception =
        assertThrows(
            ClientUnAuthorizedException.class,
            () -> authenticator.authenticate(contextWith(attestation, validPopJwt())));
    assertTrue(exception.getReason().contains("typ"));
  }

  @Test
  void throwsWhenAttestationSubDoesNotMatchClientId() throws Exception {
    String attestation =
        attestationJwt(
            ATTESTATION_TYP,
            "another-client",
            new Date(System.currentTimeMillis() + 300_000),
            Map.of("jwk", instanceKey.toPublicJWK().toJSONObject()));
    ClientUnAuthorizedException exception =
        assertThrows(
            ClientUnAuthorizedException.class,
            () -> authenticator.authenticate(contextWith(attestation, validPopJwt())));
    assertTrue(exception.getReason().contains("sub"));
  }

  @Test
  void throwsWhenAttestationIsExpired() throws Exception {
    String attestation =
        attestationJwt(
            ATTESTATION_TYP,
            CLIENT_ID,
            new Date(System.currentTimeMillis() - 60_000),
            Map.of("jwk", instanceKey.toPublicJWK().toJSONObject()));
    ClientUnAuthorizedException exception =
        assertThrows(
            ClientUnAuthorizedException.class,
            () -> authenticator.authenticate(contextWith(attestation, validPopJwt())));
    assertTrue(exception.getReason().contains("expired"));
  }

  @Test
  void throwsWhenAttestationHasNoCnf() throws Exception {
    String attestation =
        attestationJwt(
            ATTESTATION_TYP, CLIENT_ID, new Date(System.currentTimeMillis() + 300_000), null);
    ClientUnAuthorizedException exception =
        assertThrows(
            ClientUnAuthorizedException.class,
            () -> authenticator.authenticate(contextWith(attestation, validPopJwt())));
    assertTrue(exception.getReason().contains("cnf"));
  }

  @Test
  void throwsWhenCnfJwkContainsPrivateKey() throws Exception {
    String attestation =
        attestationJwt(
            ATTESTATION_TYP,
            CLIENT_ID,
            new Date(System.currentTimeMillis() + 300_000),
            Map.of("jwk", instanceKey.toJSONObject()));
    ClientUnAuthorizedException exception =
        assertThrows(
            ClientUnAuthorizedException.class,
            () -> authenticator.authenticate(contextWith(attestation, validPopJwt())));
    assertTrue(exception.getReason().contains("private"));
  }

  @Test
  void throwsWhenAttestationIsSignedByUntrustedKey() throws Exception {
    ECKey untrustedKey =
        new ECKeyGenerator(Curve.P_256)
            .keyID("attester-1")
            .algorithm(JWSAlgorithm.ES256)
            .generate();
    SignedJWT jwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.ES256)
                .keyID("attester-1")
                .type(new JOSEObjectType(ATTESTATION_TYP))
                .build(),
            new JWTClaimsSet.Builder()
                .subject(CLIENT_ID)
                .expirationTime(new Date(System.currentTimeMillis() + 300_000))
                .claim("cnf", Map.of("jwk", instanceKey.toPublicJWK().toJSONObject()))
                .build());
    jwt.sign(new ECDSASigner(untrustedKey));

    assertThrows(
        ClientUnAuthorizedException.class,
        () -> authenticator.authenticate(contextWith(jwt.serialize(), validPopJwt())));
  }

  @Test
  void throwsWhenPopTypIsInvalid() throws Exception {
    String pop = popJwt("JWT", ISSUER, UUID.randomUUID().toString(), new Date(), instanceKey);
    ClientUnAuthorizedException exception =
        assertThrows(
            ClientUnAuthorizedException.class,
            () -> authenticator.authenticate(contextWith(validAttestationJwt(), pop)));
    assertTrue(exception.getReason().contains("typ"));
  }

  @Test
  void throwsWhenPopIsSignedByDifferentKeyThanCnfJwk() throws Exception {
    ECKey anotherInstanceKey =
        new ECKeyGenerator(Curve.P_256)
            .keyID("instance-1")
            .algorithm(JWSAlgorithm.ES256)
            .generate();
    String pop =
        popJwt(POP_TYP, ISSUER, UUID.randomUUID().toString(), new Date(), anotherInstanceKey);
    ClientUnAuthorizedException exception =
        assertThrows(
            ClientUnAuthorizedException.class,
            () -> authenticator.authenticate(contextWith(validAttestationJwt(), pop)));
    assertTrue(exception.getReason().contains("signature"));
  }

  @Test
  void throwsWhenPopAudDoesNotMatchIssuer() throws Exception {
    String pop =
        popJwt(
            POP_TYP,
            "https://other-as.example.com",
            UUID.randomUUID().toString(),
            new Date(),
            instanceKey);
    ClientUnAuthorizedException exception =
        assertThrows(
            ClientUnAuthorizedException.class,
            () -> authenticator.authenticate(contextWith(validAttestationJwt(), pop)));
    assertTrue(exception.getReason().contains("aud"));
  }

  @Test
  void throwsWhenPopIatIsOutsideAcceptableWindow() throws Exception {
    String pop =
        popJwt(
            POP_TYP,
            ISSUER,
            UUID.randomUUID().toString(),
            new Date(System.currentTimeMillis() - 10 * 60 * 1000),
            instanceKey);
    ClientUnAuthorizedException exception =
        assertThrows(
            ClientUnAuthorizedException.class,
            () -> authenticator.authenticate(contextWith(validAttestationJwt(), pop)));
    assertTrue(exception.getReason().contains("iat"));
  }

  @Test
  void throwsWhenClientAttestationJwksIsNotConfigured() throws Exception {
    ClientConfiguration noJwksClient =
        JSON.read(
            "{\"client_id\":\""
                + CLIENT_ID
                + "\",\"token_endpoint_auth_method\":\"attest_jwt_client_auth\"}",
            ClientConfiguration.class);
    TokenRequestParameters parameters =
        new TokenRequestParameters(Map.of("client_id", new String[] {CLIENT_ID}));
    TokenRequestContext context =
        new TokenRequestContext(
            null,
            new ClientSecretBasic(),
            null,
            null,
            new ClientAttestationJwt(validAttestationJwt()),
            new ClientAttestationPopJwt(validPopJwt()),
            null,
            null,
            parameters,
            null,
            null,
            null,
            null,
            serverConfiguration,
            noJwksClient);

    ClientUnAuthorizedException exception =
        assertThrows(ClientUnAuthorizedException.class, () -> authenticator.authenticate(context));
    assertTrue(exception.getReason().contains("client_attestation_jwks"));
  }
}
