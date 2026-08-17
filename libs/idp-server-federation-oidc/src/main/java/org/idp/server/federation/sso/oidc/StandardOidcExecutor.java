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

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.openid.federation.sso.SsoProvider;
import org.idp.server.platform.http.HttpQueryParams;
import org.idp.server.platform.http.HttpRequestExecutor;
import org.idp.server.platform.http.HttpRequestResult;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.log.LoggerWrapper;

public class StandardOidcExecutor implements OidcSsoExecutor {

  LoggerWrapper log = LoggerWrapper.getLogger(StandardOidcExecutor.class);
  HttpRequestExecutor httpRequestExecutor;
  JsonConverter jsonConverter;

  public StandardOidcExecutor(HttpRequestExecutor httpRequestExecutor) {
    this.httpRequestExecutor = httpRequestExecutor;
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public SsoProvider type() {
    return new SsoProvider("standard");
  }

  @Override
  public OidcTokenResult requestToken(OidcTokenRequest oidcTokenRequest) {
    try {

      HttpQueryParams httpQueryParams = new HttpQueryParams(oidcTokenRequest.toMap());

      HttpRequest.Builder builder =
          HttpRequest.newBuilder()
              .uri(new URI(oidcTokenRequest.endpoint()))
              .header("Content-Type", "application/x-www-form-urlencoded")
              .header("Accept", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(httpQueryParams.params()));

      if (oidcTokenRequest.isClientSecretBasic()) {
        builder.header("Authorization", oidcTokenRequest.basicAuthenticationValue());
      }

      HttpRequest request = builder.build();

      HttpRequestResult httpResult = httpRequestExecutor.execute(request);
      JsonNodeWrapper json = httpResult.body();

      return new OidcTokenResult(httpResult.statusCode(), httpResult.headers(), json.toMap());
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      return new OidcTokenResult(
          500,
          Map.of(),
          Map.of("error", "server_error", "error_description", "unexpected network error"));
    }
  }

  @Override
  public OidcJwksResult getJwks(OidcJwksRequest oidcJwksRequest) {

    try {

      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(new URI(oidcJwksRequest.endpoint()))
              .header("Content-Type", "application/json")
              .header("Accept", "application/json")
              .GET()
              .build();

      HttpRequestResult httpResult = httpRequestExecutor.execute(request);

      String body = httpResult.body().toString();
      log.debug("JWKS response: {}", body);

      return new OidcJwksResult(httpResult.statusCode(), httpResult.headers(), body);
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      return new OidcJwksResult(500, Map.of(), "unexpected network error");
    }
  }

  @Override
  public UserinfoExecutionResult requestUserInfo(OidcUserinfoRequest oidcUserinfoRequest) {
    try {

      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(new URI(oidcUserinfoRequest.endpoint()))
              .header("Content-Type", "application/json")
              .header("Accept", "application/json")
              .header(
                  "Authorization", String.format("Bearer %s", oidcUserinfoRequest.accessToken()))
              .GET()
              .build();

      HttpRequestResult httpResult = httpRequestExecutor.execute(request);

      JsonNodeWrapper json = httpResult.body();
      HashMap<String, Object> map = new HashMap<>();
      map.put("status_code", httpResult.statusCode());
      // Single value per header, matching what oauth-extension exposes via
      // HttpRequestResult#toMap. Raw headers() would put a List here, so the same
      // $.http_request.response_headers.* would read "application/json" on one provider type and
      // ["application/json"] on another — and a mapping rule copying that into a claim would carry
      // the array through (#1800).
      map.put("response_headers", httpResult.headersAsSingleValueMap());
      map.put("response_body", json.toMap());

      // #1800: carry the status the IdP answered. clientError() / serverError() flattened it to
      // 400 / 500, so a rate limited (429) or briefly unavailable (503) upstream was
      // indistinguishable from a malformed request. This type has no response_resolve_configs to
      // fall back on — the upstream's own status is the only signal there is.
      if (httpResult.statusCode() >= 400) {
        return UserinfoExecutionResult.error(httpResult.statusCode(), map);
      }

      return UserinfoExecutionResult.success(Map.of("http_request", map));
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      return UserinfoExecutionResult.serverError(
          Map.of("error", "server_error", "error_description", "unexpected network error"));
    }
  }
}
