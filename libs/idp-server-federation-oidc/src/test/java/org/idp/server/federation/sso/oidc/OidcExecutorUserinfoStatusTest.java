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

import java.net.http.HttpRequest;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.http.HttpRequestExecutor;
import org.idp.server.platform.http.HttpRequestResult;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.junit.jupiter.api.Test;

/**
 * Pins that the {@code standard} and {@code facebook} providers carry the upstream userinfo status
 * (#1800).
 *
 * <p>These two matter more than {@code oauth-extension}, which the e2e covers: they are what Google
 * and Azure AD use, and they have no {@code response_resolve_configs} to fall back on — the
 * upstream's own status is the only signal there is. They cannot be driven from e2e because their
 * userinfo endpoint is the provider tenant's own {@code /v1/userinfo}, which always answers 200.
 */
class OidcExecutorUserinfoStatusTest {

  /** Answers a fixed status for any request, bypassing the network. */
  private static class FixedStatusExecutor extends HttpRequestExecutor {
    private final int statusCode;

    FixedStatusExecutor(int statusCode) {
      super(null, null);
      this.statusCode = statusCode;
    }

    @Override
    public HttpRequestResult execute(HttpRequest httpRequest) {
      return new HttpRequestResult(
          statusCode,
          Map.<String, List<String>>of(),
          JsonNodeWrapper.fromMap(Map.of("error", "upstream said so")));
    }
  }

  private static OidcUserinfoRequest userinfoRequest() {
    return new OidcUserinfoRequest(
        "https://idp.example.com/userinfo",
        "dummy-access-token",
        new OAuthExtensionUserinfoExecutionConfig());
  }

  @Test
  void standardProviderCarriesA429() {
    StandardOidcExecutor executor = new StandardOidcExecutor(new FixedStatusExecutor(429));

    UserinfoExecutionResult result = executor.requestUserInfo(userinfoRequest());

    // Was 400 before #1800, which told the caller "your request was wrong" for an upstream that
    // simply wanted to be retried later.
    assertEquals(429, result.statusCode());
    assertTrue(result.isError());
  }

  @Test
  void standardProviderCarriesA503() {
    StandardOidcExecutor executor = new StandardOidcExecutor(new FixedStatusExecutor(503));

    UserinfoExecutionResult result = executor.requestUserInfo(userinfoRequest());

    assertEquals(503, result.statusCode());
    assertTrue(result.isError());
  }

  @Test
  void facebookProviderCarriesA429() {
    FacebookOidcExecutor executor = new FacebookOidcExecutor(new FixedStatusExecutor(429));

    UserinfoExecutionResult result = executor.requestUserInfo(userinfoRequest());

    assertEquals(429, result.statusCode());
    assertTrue(result.isError());
  }

  @Test
  void bothProvidersStillSucceedOn200() {
    assertTrue(
        new StandardOidcExecutor(new FixedStatusExecutor(200))
            .requestUserInfo(userinfoRequest())
            .isSuccess());
    assertTrue(
        new FacebookOidcExecutor(new FixedStatusExecutor(200))
            .requestUserInfo(userinfoRequest())
            .isSuccess());
  }
}
