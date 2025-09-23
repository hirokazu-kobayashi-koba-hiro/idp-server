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

public class FacebookOidcExecutor implements OidcSsoExecutor {

  LoggerWrapper log = LoggerWrapper.getLogger(FacebookOidcExecutor.class);
  HttpRequestExecutor httpRequestExecutor;
  JsonConverter jsonConverter;

  public FacebookOidcExecutor(HttpRequestExecutor httpRequestExecutor) {
    this.httpRequestExecutor = httpRequestExecutor;
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public SsoProvider type() {
    return SupportedOidcProvider.Facebook.toSsoProvider();
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
      log.info("JWKS response: {}", body);

      return new OidcJwksResult(httpResult.statusCode(), httpResult.headers(), body);
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      return new OidcJwksResult(500, Map.of(), "unexpected network error");
    }
  }

  @Override
  public UserinfoExecutionResult requestUserInfo(OidcUserinfoRequest oidcUserinfoRequest) {
    try {

      HttpQueryParams httpQueryParams = new HttpQueryParams();
      httpQueryParams.add("fields", "id,name,email,picture");
      httpQueryParams.add("access_token", oidcUserinfoRequest.accessToken());
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(new URI(oidcUserinfoRequest.endpoint() + "?" + httpQueryParams.params()))
              .header("Content-Type", "application/json")
              .header("Accept", "application/json")
              .GET()
              .build();

      HttpRequestResult httpResult = httpRequestExecutor.execute(request);

      JsonNodeWrapper json = httpResult.body();
      HashMap<String, Object> map = new HashMap<>();
      map.put("staus_code", httpResult.statusCode());
      map.put("response_headers", httpResult.headers());
      map.put("response_body", json.toMap());

      if (httpResult.statusCode() >= 400 && httpResult.statusCode() < 500) {
        return UserinfoExecutionResult.clientError(map);
      }

      if (httpResult.statusCode() >= 500) {
        return UserinfoExecutionResult.serverError(map);
      }

      return UserinfoExecutionResult.success(Map.of("http_request", map));
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      return UserinfoExecutionResult.serverError(
          Map.of("error", "server_error", "error_description", "unexpected network error"));
    }
  }
}
