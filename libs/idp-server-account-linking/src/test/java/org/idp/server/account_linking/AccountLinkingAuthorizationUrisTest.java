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

package org.idp.server.account_linking;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.account_linking.exception.AccountLinkingInvalidRequestException;
import org.idp.server.core.openid.identity.UserIdentifier;
import org.idp.server.core.openid.oauth.AccountLinkingReauthentication;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.federation.sso.oidc.OidcSsoConfiguration;
import org.idp.server.platform.json.JsonConverter;
import org.junit.jupiter.api.Test;

/** The authorization requests a link sends the browser to: at the external IdP, and to sign in. */
class AccountLinkingAuthorizationUrisTest {

  static final String VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";

  static AccountLinkingSession session(String requestedScope) {
    return new AccountLinkingSession.Builder()
        .state(new AccountLinkingState("link-state"))
        .userIdentifier(new UserIdentifier("11111111-1111-1111-1111-111111111111"))
        .requestedClientId(new RequestedClientId("rp-client"))
        .provider(new ExternalIdpProvider("example-idp"))
        .codeVerifier(VERIFIER)
        .nonce("link-nonce")
        .requestedScope(requestedScope)
        .expiresAt(LocalDateTime.now().plusMinutes(15))
        .build();
  }

  static OidcSsoConfiguration provider() {
    return JsonConverter.snakeCaseInstance()
        .read(
            Map.of(
                "client_id", "linking-client",
                "redirect_uri", "https://idp.example.com/t/v1/linking/callback/example-idp",
                "authorization_endpoint", "https://external.example.com/authorize",
                "scopes_supported", List.of("openid", "email")),
            OidcSsoConfiguration.class);
  }

  static ClientConfiguration client(List<String> redirectUris, List<String> linkingAcrValues) {
    Map<String, Object> extension = new HashMap<>();
    extension.put("linking_acr_values", linkingAcrValues);
    return JsonConverter.snakeCaseInstance()
        .read(
            Map.of("client_id", "rp-client", "redirect_uris", redirectUris, "extension", extension),
            ClientConfiguration.class);
  }

  static Map<String, String> query(String uri) {
    Map<String, String> params = new HashMap<>();
    for (String pair : URI.create(uri).getRawQuery().split("&")) {
      String[] kv = pair.split("=", 2);
      params.put(kv[0], java.net.URLDecoder.decode(kv[1], java.nio.charset.StandardCharsets.UTF_8));
    }
    return params;
  }

  @Test
  void asksTheExternalIdpWithTheLinksStateNonceAndPkce() {
    String uri = new AccountLinkingExternalAuthorizationUri(provider(), session("openid")).value();

    assertTrue(uri.startsWith("https://external.example.com/authorize?"));
    Map<String, String> params = query(uri);
    assertEquals("linking-client", params.get("client_id"));
    assertEquals(
        "https://idp.example.com/t/v1/linking/callback/example-idp", params.get("redirect_uri"));
    assertEquals("code", params.get("response_type"));
    assertEquals("link-state", params.get("state"));
    assertEquals("link-nonce", params.get("nonce"));
    assertEquals("openid", params.get("scope"));
    assertEquals(new AccountLinkingPkce(VERIFIER).codeChallenge(), params.get("code_challenge"));
    assertEquals("S256", params.get("code_challenge_method"));
  }

  @Test
  void asksTheExternalIdpForTheProvidersScopesWhenNoneWasRequested() {
    Map<String, String> params =
        query(new AccountLinkingExternalAuthorizationUri(provider(), session(null)).value());

    assertEquals("openid email", params.get("scope"));
  }

  @Test
  void signsInAgainAsTheLinksUserThroughTheClientsOwnRequest() {
    String uri =
        new AccountLinkingSignInUri(
                "https://idp.example.com/t/v1/authorizations",
                client(
                    List.of("https://rp.example.com/cb", "https://rp.example.com/other"),
                    List.of()),
                session(null))
            .value();

    assertTrue(uri.startsWith("https://idp.example.com/t/v1/authorizations?"));
    Map<String, String> params = query(uri);
    assertEquals("rp-client", params.get("client_id"));
    assertEquals("https://rp.example.com/cb", params.get("redirect_uri"));
    assertEquals("login", params.get("prompt"));
    assertEquals("sub:11111111-1111-1111-1111-111111111111", params.get("login_hint"));
    assertEquals("link-state", params.get(AccountLinkingReauthentication.STATE_PARAMETER));
    assertEquals("S256", params.get("code_challenge_method"));
    assertFalse(params.containsKey("acr_values"));
    // The sign-in's own state is not the link's: the link's travels only as its own parameter.
    assertNotEquals("link-state", params.get("state"));
  }

  @Test
  void signsInAgainWithTheLinkingAcrValues() {
    Map<String, String> params =
        query(
            new AccountLinkingSignInUri(
                    "https://idp.example.com/t/v1/authorizations",
                    client(
                        List.of("https://rp.example.com/cb"),
                        List.of("urn:acr:fido", "urn:acr:mfa")),
                    session(null))
                .value());

    assertEquals("urn:acr:fido urn:acr:mfa", params.get("acr_values"));
  }

  @Test
  void cannotSignInAgainForAClientWithoutARedirectUri() {
    assertThrows(
        AccountLinkingInvalidRequestException.class,
        () ->
            new AccountLinkingSignInUri(
                "https://idp.example.com/t/v1/authorizations",
                client(List.of(), List.of()),
                session(null)));
  }
}
