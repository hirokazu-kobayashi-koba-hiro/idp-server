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

package org.idp.server.federation.sso.oidc;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.federation.sso.SsoProvider;
import org.idp.server.core.openid.federation.sso.oidc.OidcSsoSession;
import org.junit.jupiter.api.Test;

/**
 * Regression for issue #1776 (review of PR #1777): the id_token {@code aud} check must reject when
 * {@code aud} is missing or JSON {@code null}, per OIDC Core §3.1.3.7 step 3 ("The ID Token MUST be
 * rejected if the ID Token does not list the Client as a valid audience").
 *
 * <p>Making {@code hasAud()} null-aware turned the prior {@code hasAud() && !contains(...)} guard
 * from an accidental fail-closed (NPE → 500) into a fail-open (skip → accept) for {@code aud:null}.
 * A null/absent audience must be rejected, otherwise an id_token minted for a different client is
 * accepted for this one. The audience check runs before signature verification, so an unsigned
 * (alg:none) id_token exercises it directly.
 */
class OidcSsoExecutorAudVerificationTest {

  private static final String ISSUER = "https://idp.example.com";
  private static final String CLIENT_ID = "client-1";

  private String plainIdToken(String payloadJson) {
    Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
    String header = enc.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
    String payload = enc.encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
    return header + "." + payload + ".";
  }

  private OidcSsoConfiguration configuration() {
    return new OidcSsoConfiguration(
        "oidc", ISSUER, null, null, CLIENT_ID, null, null, null, null, null, null, null, null, null,
        null, null, 0L, 0L, false);
  }

  private OidcSsoExecutor executor() {
    return new OidcSsoExecutor() {
      @Override
      public SsoProvider type() {
        return new SsoProvider("oidc");
      }

      @Override
      public OidcTokenResult requestToken(OidcTokenRequest oidcTokenRequest) {
        return null;
      }

      @Override
      public OidcJwksResult getJwks(OidcJwksRequest oidcJwksRequest) {
        return null;
      }

      @Override
      public UserinfoExecutionResult requestUserInfo(OidcUserinfoRequest oidcUserinfoRequest) {
        return null;
      }
    };
  }

  private IdTokenVerificationResult verify(String payloadJson) {
    OidcTokenResult tokenResult =
        new OidcTokenResult(200, Map.of(), Map.of("id_token", plainIdToken(payloadJson)));
    OidcJwksResult jwksResult = new OidcJwksResult(200, Map.<String, List<String>>of(), "{}");
    return executor().verifyIdToken(configuration(), new OidcSsoSession(), jwksResult, tokenResult);
  }

  @Test
  void nullAudienceIsRejected() {
    IdTokenVerificationResult result =
        verify("{\"iss\":\"" + ISSUER + "\",\"aud\":null,\"exp\":9999999999}");

    assertTrue(result.isError());
    assertEquals("id_token does not contain aud.", result.data().get("error_description"));
  }

  @Test
  void absentAudienceIsRejected() {
    IdTokenVerificationResult result = verify("{\"iss\":\"" + ISSUER + "\",\"exp\":9999999999}");

    assertTrue(result.isError());
    assertEquals("id_token does not contain aud.", result.data().get("error_description"));
  }

  @Test
  void audienceForAnotherClientIsRejected() {
    IdTokenVerificationResult result =
        verify("{\"iss\":\"" + ISSUER + "\",\"aud\":[\"other-client\"],\"exp\":9999999999}");

    assertTrue(result.isError());
    assertTrue(
        ((String) result.data().get("error_description")).contains("aud does not contain"),
        String.valueOf(result.data().get("error_description")));
  }
}
