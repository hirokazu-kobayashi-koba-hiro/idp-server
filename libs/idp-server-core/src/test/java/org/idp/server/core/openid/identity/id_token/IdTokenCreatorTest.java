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

package org.idp.server.core.openid.identity.id_token;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.idp.server.core.openid.authentication.Authentication;
import org.idp.server.core.openid.grant_management.grant.AuthorizationGrant;
import org.idp.server.core.openid.grant_management.grant.AuthorizationGrantBuilder;
import org.idp.server.core.openid.grant_management.grant.GrantIdTokenClaims;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.type.oauth.AccessTokenEntity;
import org.idp.server.core.openid.oauth.type.oauth.AuthorizationCode;
import org.idp.server.core.openid.oauth.type.oauth.GrantType;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.core.openid.oauth.type.oauth.Scopes;
import org.idp.server.core.openid.oauth.type.oauth.State;
import org.idp.server.core.openid.oauth.type.oidc.IdToken;
import org.idp.server.core.openid.oauth.type.oidc.Nonce;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Regression test for Issue #1491.
 *
 * <p>at_hash / c_hash / s_hash MUST be derived with the hash algorithm of the ID Token signature
 * algorithm (OIDC Core 3.3.2.11), not a hardcoded "ES256"/SHA-256. The hash algorithm is verified
 * against the signature algorithm actually written into the JWS header: ES256→SHA-256,
 * ES384→SHA-384, ES512→SHA-512. Before the fix, ES384/ES512 produced a SHA-256 hash and these
 * assertions fail.
 */
class IdTokenCreatorTest {

  // EC signing keys (generated for tests). The key's "alg" drives both the JWS signature and the
  // at_hash/c_hash/s_hash hash algorithm — they must match.
  private static final String ES256_JWK =
      "{\"kty\":\"EC\",\"kid\":\"id-token-es256\",\"use\":\"sig\",\"alg\":\"ES256\",\"crv\":\"P-256\",\"x\":\"Y8Oi9B2da4SCGAA5SzKV821P4U3cUFLxyWmvD-zMTQ4\",\"y\":\"V9D3xbGoNoOqYzhDgsUI0ZpMtsRhButt5xjZXU-sKRo\",\"d\":\"NXcffy4YqYk3G6_DmRoYXjEp5IV3q875ayAIhZ_Zp_Y\"}";
  private static final String ES384_JWK =
      "{\"kty\":\"EC\",\"kid\":\"id-token-es384\",\"use\":\"sig\",\"alg\":\"ES384\",\"crv\":\"P-384\",\"x\":\"mo7Wa_wb0da4tUdLn7ubaW0Ev0vbI2MZ7jEzOGwTG5uKMTb_GTCiPKIC5Iiyxf0w\",\"y\":\"2iO3bjEx5OQhpH6lgJLuHVeF3hxrS2DV10zXmAYcXP3tiWwbfD3MX5rwIRKVGhdC\",\"d\":\"LqSTsPwO70CNJ2RAiTQGdIXulVKbLM-L3lDZEUH0dzbtlfHBQbyc8g2EGYEmQLEv\"}";
  private static final String ES512_JWK =
      "{\"kty\":\"EC\",\"kid\":\"id-token-es512\",\"use\":\"sig\",\"alg\":\"ES512\",\"crv\":\"P-521\",\"x\":\"AIxav3uauc_vfpoZTXSYtkzbAkMzK1k443dS-ex3ku2eRTcHwBpMIP1k6sO6r9_BFc0BXiTPZPoHJLwLv-9VwP1O\",\"y\":\"AC3exbGCXaSxMq17U9qdp6amVzfLX14khIBSdKYHi8b2aq_xVIGE5u9YKDDkCF-ZTeiBc9-ofqmY3m4epFYx-c9A\",\"d\":\"AOGLmKvWftoTMZZJwDX1qLt9iV1W4aQZDJ686RLJSOVRSzqgIwpnd_Aa98cMu-9mHEKJaZCrn5QqcVRTdkGPq5N3\"}";

  static Stream<Arguments> signingAlgorithms() {
    return Stream.of(
        Arguments.of(ES256_JWK, "id-token-es256", "ES256", "SHA-256"),
        Arguments.of(ES384_JWK, "id-token-es384", "ES384", "SHA-384"),
        Arguments.of(ES512_JWK, "id-token-es512", "ES512", "SHA-512"));
  }

  @ParameterizedTest(name = "{2} -> {3}")
  @MethodSource("signingAlgorithms")
  void at_hash_c_hash_s_hash_use_the_id_token_signature_hash_algorithm(
      String jwk, String keyId, String expectedAlg, String expectedShaAlg) throws Exception {

    String accessToken = "access-token-" + UUID.randomUUID();
    String authorizationCode = "authz-code-" + UUID.randomUUID();
    String state = "state-" + UUID.randomUUID();

    IdTokenCustomClaims customClaims =
        new IdTokenCustomClaimsBuilder()
            .add(new AccessTokenEntity(accessToken))
            .add(new AuthorizationCode(authorizationCode))
            .add(new State(state))
            .add(new Nonce("nonce-value"))
            .build();

    User user = new User().setSub(UUID.randomUUID().toString());
    Authentication authentication = new Authentication();
    AuthorizationGrant grant =
        new AuthorizationGrantBuilder(
                new TenantIdentifier(UUID.randomUUID().toString()),
                new RequestedClientId("test-client"),
                GrantType.authorization_code,
                new Scopes("openid"))
            .add(user)
            .add(authentication)
            .add(new GrantIdTokenClaims())
            .build();

    IdToken idToken =
        IdTokenCreator.getInstance()
            .createIdToken(
                user,
                authentication,
                grant,
                customClaims,
                new RequestedClaimsPayload(),
                serverConfiguration(jwk, keyId),
                clientConfiguration());

    Map<String, Object> header = decodeSegment(idToken.value(), 0);
    Map<String, Object> payload = decodeSegment(idToken.value(), 1);

    assertEquals(expectedAlg, header.get("alg"), "JWS header alg");
    assertEquals(leftHalfHash(accessToken, expectedShaAlg), payload.get("at_hash"), "at_hash");
    assertEquals(leftHalfHash(authorizationCode, expectedShaAlg), payload.get("c_hash"), "c_hash");
    assertEquals(leftHalfHash(state, expectedShaAlg), payload.get("s_hash"), "s_hash");
  }

  private static AuthorizationServerConfiguration serverConfiguration(String jwk, String keyId) {
    Map<String, Object> extension = new HashMap<>();
    extension.put("idTokenSignedKeyId", keyId);
    extension.put("idTokenDuration", 3600);
    extension.put("idTokenStrictMode", false);

    Map<String, Object> config = new HashMap<>();
    config.put("issuer", "https://idp.example.com");
    config.put("jwks", "{\"keys\":[" + jwk + "]}");
    config.put("extension", extension);

    return JsonConverter.defaultInstance().read(config, AuthorizationServerConfiguration.class);
  }

  private static ClientConfiguration clientConfiguration() {
    Map<String, Object> config = new HashMap<>();
    config.put("clientId", "test-client");
    config.put("extension", new HashMap<>());
    return JsonConverter.defaultInstance().read(config, ClientConfiguration.class);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> decodeSegment(String jwt, int index) {
    String segment = jwt.split("\\.")[index];
    String json = new String(Base64.getUrlDecoder().decode(segment), StandardCharsets.UTF_8);
    return JsonConverter.defaultInstance().read(json, Map.class);
  }

  /** Independent oracle: base64url(no padding) of the left-most half of the digest. */
  private static String leftHalfHash(String input, String shaAlgorithm) throws Exception {
    MessageDigest messageDigest = MessageDigest.getInstance(shaAlgorithm);
    byte[] digest = messageDigest.digest(input.getBytes(StandardCharsets.US_ASCII));
    byte[] half = Arrays.copyOfRange(digest, 0, digest.length / 2);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(half);
  }
}
