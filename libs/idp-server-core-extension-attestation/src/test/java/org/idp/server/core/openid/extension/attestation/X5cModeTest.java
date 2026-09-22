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
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.util.Base64;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.util.Date;
import java.util.List;
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
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.core.openid.token.TokenRequestContext;
import org.idp.server.core.openid.token.TokenRequestParameters;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Certificate chain model: the Client Attester signs the Client Attestation JWT and carries its
 * certificate chain in {@code x5c}, and the server validates that chain to a configured root
 * ({@code client_attestation_trust_source = x5c}).
 *
 * <p>draft-ietf-oauth-attestation-based-client-auth-11 Section 10.8 describes this as one of the
 * shapes trust can take. What distinguishes it from {@code attester_jwks} is that the attester's
 * signing key is not configured anywhere: the deployment pins a root, and a key replaced under that
 * root is accepted without the client being touched. The first test is what pays for the mode; the
 * rest are the ways a chain can fail to mean anything.
 */
class X5cModeTest {

  static final JsonConverter JSON = JsonConverter.snakeCaseInstance();
  static final String CLIENT_ID = "x5c-client";
  static final String ISSUER = "https://idp.example.com/tenant-1";
  static final String ATTESTATION_TYP = "oauth-client-attestation+jwt";
  static final String POP_TYP = "oauth-client-attestation-pop+jwt";
  static final String TENANT_ID = "1e68932e-ed4a-43e7-b412-460665e42df3";

  AttesterCertificateFixture fixture;
  ECKey instanceKey;
  AttestJwtClientAuthAuthenticator authenticator;
  AuthorizationServerConfiguration serverConfiguration;

  @BeforeEach
  void setup() throws Exception {
    fixture = new AttesterCertificateFixture();
    instanceKey = new ECKeyGenerator(Curve.P_256).keyID("instance-key").generate();
    authenticator =
        new AttestJwtClientAuthAuthenticator(
            new ClientAttestationKeyResolvers(new StubClientInstanceQueryRepository()),
            new StubClientAttestationChallengeRepository());
    serverConfiguration =
        JSON.read("{\"issuer\":\"" + ISSUER + "\"}", AuthorizationServerConfiguration.class);
  }

  private ClientConfiguration clientTrusting(List<String> roots) {
    return JSON.read(
        JSON.write(
            Map.of(
                "client_id",
                CLIENT_ID,
                "token_endpoint_auth_method",
                "attest_jwt_client_auth",
                "extension",
                Map.of(
                    "client_attestation_trust_source",
                    "x5c",
                    "client_attestation_trusted_root_certificates",
                    roots))),
        ClientConfiguration.class);
  }

  /** A Client Attestation JWT signed by {@code signingKey}, carrying {@code x5c}. */
  private String attestationJwt(ECKey signingKey, List<String> x5c) throws Exception {
    JWSHeader.Builder header =
        new JWSHeader.Builder(JWSAlgorithm.ES256).type(new JOSEObjectType(ATTESTATION_TYP));
    if (x5c != null) {
      header.x509CertChain(x5c.stream().map(Base64::from).toList());
    }
    SignedJWT jwt =
        new SignedJWT(
            header.build(),
            new JWTClaimsSet.Builder()
                .subject(CLIENT_ID)
                .issueTime(new Date())
                .expirationTime(new Date(System.currentTimeMillis() + 300_000))
                .claim("cnf", Map.of("jwk", instanceKey.toPublicJWK().toJSONObject()))
                .build());
    jwt.sign(new ECDSASigner(signingKey));
    return jwt.serialize();
  }

  private String popJwt() throws Exception {
    SignedJWT jwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.ES256).type(new JOSEObjectType(POP_TYP)).build(),
            new JWTClaimsSet.Builder()
                .audience(ISSUER)
                .jwtID(UUID.randomUUID().toString())
                .issueTime(new Date())
                .build());
    jwt.sign(new ECDSASigner(instanceKey));
    return jwt.serialize();
  }

  private TokenRequestContext contextWith(String attestation, ClientConfiguration client)
      throws Exception {
    Tenant tenant =
        new Tenant(
            new TenantIdentifier(TENANT_ID),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            true);
    return new TokenRequestContext(
        tenant,
        new RequestedClientId(CLIENT_ID),
        new ClientSecretBasic(),
        null,
        null,
        new ClientAttestationJwt(attestation),
        new ClientAttestationPopJwt(popJwt()),
        null,
        null,
        new TokenRequestParameters(Map.of("client_id", new String[] {CLIENT_ID})),
        null,
        null,
        null,
        null,
        serverConfiguration,
        clientConfiguration(client));
  }

  private ClientConfiguration clientConfiguration(ClientConfiguration client) {
    return client;
  }

  @Nested
  class TrustedChain {

    @Test
    void authenticatesWhenTheChainLeadsToTheConfiguredRoot() throws Exception {
      AttesterCertificateFixture.Attester attester = fixture.issueAttester("attester-1");

      ClientCredentials credentials =
          authenticator.authenticate(
              contextWith(
                  attestationJwt(attester.signingKey(), attester.x5c()),
                  clientTrusting(List.of(fixture.rootBase64()))));

      assertEquals(
          ClientAuthenticationType.attest_jwt_client_auth, credentials.clientAuthenticationType());
      assertEquals(CLIENT_ID, credentials.clientId().value());
    }

    @Test
    void authenticatesWhenTheChainOmitsTheTrustAnchor() throws Exception {
      // HAIP: "The X.509 certificate of the trust anchor MUST NOT be included". The verifier holds
      // the root already, so a chain that stops below it has to verify just the same.
      AttesterCertificateFixture.Attester attester = fixture.issueAttester("attester-1");

      assertDoesNotThrow(
          () ->
              authenticator.authenticate(
                  contextWith(
                      attestationJwt(attester.signingKey(), attester.x5cWithoutRoot()),
                      clientTrusting(List.of(fixture.rootBase64())))));
    }

    @Test
    void acceptsAReplacedSigningKeyUnderTheSameRoot() throws Exception {
      // This is what the mode is for. The attester rotates to a certificate the deployment has
      // never seen, and nothing about the client's configuration changes.
      ClientConfiguration client = clientTrusting(List.of(fixture.rootBase64()));
      AttesterCertificateFixture.Attester rotated = fixture.issueAttester("attester-2");

      assertDoesNotThrow(
          () ->
              authenticator.authenticate(
                  contextWith(attestationJwt(rotated.signingKey(), rotated.x5c()), client)));
    }
  }

  @Nested
  class UntrustedChain {

    @Test
    void rejectsAChainThatDoesNotLeadToTheConfiguredRoot() throws Exception {
      // The attacker signs their own chain. Everything inside it is theirs to choose, so only the
      // root check decides anything.
      AttesterCertificateFixture attacker = new AttesterCertificateFixture();
      AttesterCertificateFixture.Attester forged = attacker.issueAttester("attacker");

      ClientUnAuthorizedException exception =
          assertThrows(
              ClientUnAuthorizedException.class,
              () ->
                  authenticator.authenticate(
                      contextWith(
                          attestationJwt(forged.signingKey(), forged.x5c()),
                          clientTrusting(List.of(fixture.rootBase64())))));

      assertTrue(exception.getReason().contains("no trusted client attestation key"));
    }

    @Test
    void rejectsWhenTheHeaderCarriesNoChain() throws Exception {
      AttesterCertificateFixture.Attester attester = fixture.issueAttester("attester-1");

      ClientUnAuthorizedException exception =
          assertThrows(
              ClientUnAuthorizedException.class,
              () ->
                  authenticator.authenticate(
                      contextWith(
                          attestationJwt(attester.signingKey(), null),
                          clientTrusting(List.of(fixture.rootBase64())))));

      assertTrue(exception.getReason().contains("no trusted client attestation key"));
    }

    @Test
    void rejectsWhenNoRootIsConfigured() throws Exception {
      // Without a root there is nothing to validate against, so the presented chain would be
      // trusted because it was presented. Refused instead.
      AttesterCertificateFixture.Attester attester = fixture.issueAttester("attester-1");

      ClientUnAuthorizedException exception =
          assertThrows(
              ClientUnAuthorizedException.class,
              () ->
                  authenticator.authenticate(
                      contextWith(
                          attestationJwt(attester.signingKey(), attester.x5c()),
                          clientTrusting(List.of()))));

      assertTrue(exception.getReason().contains("no trusted client attestation key"));
    }

    @Test
    void rejectsAChainWhoseLeafSignedForAnotherKey() throws Exception {
      // The chain verifies to the root, but the JWT was signed by a key the chain does not
      // certify. The JOSE layer is what catches this, and it has to.
      AttesterCertificateFixture.Attester attester = fixture.issueAttester("attester-1");
      ECKey otherKey = new ECKeyGenerator(Curve.P_256).generate();

      ClientUnAuthorizedException exception =
          assertThrows(
              ClientUnAuthorizedException.class,
              () ->
                  authenticator.authenticate(
                      contextWith(
                          attestationJwt(otherKey, attester.x5c()),
                          clientTrusting(List.of(fixture.rootBase64())))));

      assertNotNull(exception.getReason());
    }
  }
}
