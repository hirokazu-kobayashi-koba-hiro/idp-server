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

  /** Answers a fixed status and headers for any request, bypassing the network. */
  private static class FixedStatusExecutor extends HttpRequestExecutor {
    private final int statusCode;
    private final Map<String, List<String>> headers;

    FixedStatusExecutor(int statusCode) {
      this(statusCode, Map.of());
    }

    FixedStatusExecutor(int statusCode, Map<String, List<String>> headers) {
      // Relies on the constructor not using its arguments: it only assembles a HttpRequestBuilder
      // and a HttpRetryStrategy, and execute(HttpRequest) is overridden below so neither is
      // reached. If either argument starts being validated, give this a real stub instead.
      super(null, null);
      this.statusCode = statusCode;
      this.headers = headers;
    }

    @Override
    public HttpRequestResult execute(HttpRequest httpRequest) {
      return new HttpRequestResult(
          statusCode, headers, JsonNodeWrapper.fromMap(Map.of("error", "upstream said so")));
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
  void bothProvidersExposeHeadersAsSingleValues() {
    // Raw headers() would put a List here, so $.http_request.response_headers.content-type would
    // read ["application/json"] on standard / facebook and "application/json" on oauth-extension.
    // A mapping rule copying that into a claim would carry the array through (#1800). Both are
    // asserted because leaving one behind is the very asymmetry this change removes.
    Map<String, List<String>> headers = Map.of("content-type", List.of("application/json"));

    assertEquals(
        "application/json",
        responseHeaderOf(
            new StandardOidcExecutor(new FixedStatusExecutor(200, headers))
                .requestUserInfo(userinfoRequest()),
            "content-type"));
    assertEquals(
        "application/json",
        responseHeaderOf(
            new FacebookOidcExecutor(new FixedStatusExecutor(200, headers))
                .requestUserInfo(userinfoRequest()),
            "content-type"));
  }

  private static Object responseHeaderOf(UserinfoExecutionResult result, String headerName) {
    @SuppressWarnings("unchecked")
    Map<String, Object> httpRequest = (Map<String, Object>) result.contents().get("http_request");
    @SuppressWarnings("unchecked")
    Map<String, Object> responseHeaders = (Map<String, Object>) httpRequest.get("response_headers");
    return responseHeaders.get(headerName);
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
