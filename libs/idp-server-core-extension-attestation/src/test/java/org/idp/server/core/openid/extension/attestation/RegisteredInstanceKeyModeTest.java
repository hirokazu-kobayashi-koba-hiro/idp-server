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
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import org.idp.server.core.openid.clientinstance.ClientInstance;
import org.idp.server.core.openid.clientinstance.ClientInstanceStatus;
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
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Self-signed model: the Client Instance signs its own Client Attestation JWT and the server trusts
 * the Client Instance Key it registered ({@code client_attestation_trust_source =
 * registered_instance_key}).
 */
class RegisteredInstanceKeyModeTest {

  static final JsonConverter JSON = JsonConverter.snakeCaseInstance();
  static final String CLIENT_ID = "self-signed-client";
  static final String ISSUER = "https://idp.example.com/tenant-1";
  static final String INSTANCE_ID = "instance-abc";
  static final String ATTESTATION_TYP = "oauth-client-attestation+jwt";
  static final String POP_TYP = "oauth-client-attestation-pop+jwt";
  static final String TENANT_ID = "1e68932e-ed4a-43e7-b412-460665e42df3";

  ECKey instanceKey;
  StubClientInstanceQueryRepository repository;
  AttestJwtClientAuthAuthenticator authenticator;
  ClientConfiguration clientConfiguration;
  AuthorizationServerConfiguration serverConfiguration;

  @BeforeEach
  void setup() throws Exception {
    instanceKey =
        new ECKeyGenerator(Curve.P_256).keyID(INSTANCE_ID).algorithm(JWSAlgorithm.ES256).generate();
    repository = new StubClientInstanceQueryRepository();
    repository.put(activeInstance(instanceKey));
    authenticator =
        new AttestJwtClientAuthAuthenticator(
            new ClientAttestationKeyResolvers(repository),
            new StubClientAttestationChallengeRepository());

    clientConfiguration =
        JSON.read(
            JSON.write(
                Map.of(
                    "client_id",
                    CLIENT_ID,
                    "token_endpoint_auth_method",
                    "attest_jwt_client_auth",
                    "extension",
                    Map.of("client_attestation_trust_source", "registered_instance_key"))),
            ClientConfiguration.class);
    serverConfiguration =
        JSON.read("{\"issuer\":\"" + ISSUER + "\"}", AuthorizationServerConfiguration.class);
  }

  private ClientInstance activeInstance(ECKey key) {
    return new ClientInstance(
        INSTANCE_ID,
        TENANT_ID,
        CLIENT_ID,
        key.toPublicJWK().toJSONObject(),
        ClientInstanceStatus.active.name(),
        Map.of(),
        null,
        LocalDateTime.now(),
        LocalDateTime.now(),
        null,
        null);
  }

  private String selfSignedAttestationJwt(
      String kid, Date iat, Date exp, Object cnf, ECKey signingKey) throws Exception {
    JWSHeader header =
        new JWSHeader.Builder(JWSAlgorithm.ES256)
            .keyID(kid)
            .type(new JOSEObjectType(ATTESTATION_TYP))
            .build();
    JWTClaimsSet.Builder claims =
        new JWTClaimsSet.Builder().subject(CLIENT_ID).issueTime(iat).expirationTime(exp);
    if (cnf != null) {
      claims.claim("cnf", cnf);
    }
    SignedJWT jwt = new SignedJWT(header, claims.build());
    jwt.sign(new ECDSASigner(signingKey));
    return jwt.serialize();
  }

  private String validAttestationJwt() throws Exception {
    return selfSignedAttestationJwt(
        INSTANCE_ID,
        new Date(),
        new Date(System.currentTimeMillis() + 300_000),
        Map.of("jwk", instanceKey.toPublicJWK().toJSONObject()),
        instanceKey);
  }

  private String popJwt(ECKey signingKey) throws Exception {
    SignedJWT jwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.ES256).type(new JOSEObjectType(POP_TYP)).build(),
            new JWTClaimsSet.Builder()
                .audience(ISSUER)
                .jwtID(UUID.randomUUID().toString())
                .issueTime(new Date())
                .build());
    jwt.sign(new ECDSASigner(signingKey));
    return jwt.serialize();
  }

  private TokenRequestContext contextWith(String attestationJwt, String popJwt) {
    TokenRequestParameters parameters =
        new TokenRequestParameters(Map.of("client_id", new String[] {CLIENT_ID}));
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
  void authenticatesWithSelfSignedAttestationVerifiedByRegisteredInstanceKey() throws Exception {
    ClientCredentials credentials =
        authenticator.authenticate(contextWith(validAttestationJwt(), popJwt(instanceKey)));

    assertEquals(
        ClientAuthenticationType.attest_jwt_client_auth, credentials.clientAuthenticationType());
    assertEquals(CLIENT_ID, credentials.clientId().value());
  }

  @Test
  void rejectsWhenInstanceIsNotRegistered() throws Exception {
    String attestation =
        selfSignedAttestationJwt(
            "unknown-instance",
            new Date(),
            new Date(System.currentTimeMillis() + 300_000),
            Map.of("jwk", instanceKey.toPublicJWK().toJSONObject()),
            instanceKey);

    ClientUnAuthorizedException exception =
        assertThrows(
            ClientUnAuthorizedException.class,
            () -> authenticator.authenticate(contextWith(attestation, popJwt(instanceKey))));
    assertTrue(exception.getReason().contains("no trusted client attestation key"));
  }

  @Test
  void rejectsWhenInstanceIsRevoked() throws Exception {
    ClientInstance revoked =
        new ClientInstance(
            INSTANCE_ID,
            TENANT_ID,
            CLIENT_ID,
            instanceKey.toPublicJWK().toJSONObject(),
            ClientInstanceStatus.revoked.name(),
            Map.of(),
            null,
            LocalDateTime.now(),
            LocalDateTime.now(),
            null,
            LocalDateTime.now());
    repository.put(revoked);

    ClientUnAuthorizedException exception =
        assertThrows(
            ClientUnAuthorizedException.class,
            () ->
                authenticator.authenticate(
                    contextWith(validAttestationJwt(), popJwt(instanceKey))));
    assertTrue(exception.getReason().contains("no trusted client attestation key"));
  }

  @Test
  void rejectsWhenSignedByKeyOtherThanRegisteredInstanceKey() throws Exception {
    ECKey attackerKey =
        new ECKeyGenerator(Curve.P_256).keyID(INSTANCE_ID).algorithm(JWSAlgorithm.ES256).generate();
    String attestation =
        selfSignedAttestationJwt(
            INSTANCE_ID,
            new Date(),
            new Date(System.currentTimeMillis() + 300_000),
            Map.of("jwk", attackerKey.toPublicJWK().toJSONObject()),
            attackerKey);

    assertThrows(
        ClientUnAuthorizedException.class,
        () -> authenticator.authenticate(contextWith(attestation, popJwt(attackerKey))));
  }

  @Test
  void rejectsWhenCnfJwkIsNotTheKeyThatSignedTheAttestation() throws Exception {
    ECKey otherKey =
        new ECKeyGenerator(Curve.P_256).keyID("other").algorithm(JWSAlgorithm.ES256).generate();
    // signed by the registered instance key, but cnf points at another key:
    // the PoP would then prove possession of a key the server never registered.
    String attestation =
        selfSignedAttestationJwt(
            INSTANCE_ID,
            new Date(),
            new Date(System.currentTimeMillis() + 300_000),
            Map.of("jwk", otherKey.toPublicJWK().toJSONObject()),
            instanceKey);

    ClientUnAuthorizedException exception =
        assertThrows(
            ClientUnAuthorizedException.class,
            () -> authenticator.authenticate(contextWith(attestation, popJwt(otherKey))));
    assertTrue(exception.getReason().contains("registered client instance key"));
  }

  @Test
  void rejectsWhenLifetimeExceedsServerPolicy() throws Exception {
    String attestation =
        selfSignedAttestationJwt(
            INSTANCE_ID,
            new Date(),
            new Date(System.currentTimeMillis() + 48L * 60 * 60 * 1000),
            Map.of("jwk", instanceKey.toPublicJWK().toJSONObject()),
            instanceKey);

    ClientUnAuthorizedException exception =
        assertThrows(
            ClientUnAuthorizedException.class,
            () -> authenticator.authenticate(contextWith(attestation, popJwt(instanceKey))));
    assertTrue(exception.getReason().contains("lifetime"));
  }
}
