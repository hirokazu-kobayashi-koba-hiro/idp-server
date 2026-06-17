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

package org.idp.server.platform.oauth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.idp.server.platform.http.SsrfProtectedHttpClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * #1384: when the external token endpoint rejects the request (non-2xx), the OAuth resolvers must
 * raise a dedicated {@link OAuthAuthorizationException} carrying the upstream status/body — not a
 * generic network error — so the boundary can return a meaningful error instead of a 500.
 */
@ExtendWith(MockitoExtension.class)
class OAuthAuthorizationResolverErrorTest {

  @Mock SsrfProtectedHttpClient httpClient;
  @Mock OAuthAuthorizationConfiguration config;

  @SuppressWarnings("unchecked")
  private void stubResponse(int status, String body) {
    HttpResponse<String> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(status);
    lenient().when(response.body()).thenReturn(body);
    when(httpClient.send(any(HttpRequest.class))).thenReturn(response);
  }

  private void stubConfig() {
    when(config.toRequestValues()).thenReturn(Map.of("grant_type", "password"));
    when(config.tokenEndpoint()).thenReturn("https://idp.example.com/token");
  }

  @Test
  void passwordResolver_throwsOAuthAuthorizationExceptionOn401() {
    stubConfig();
    stubResponse(401, "{\"error\":\"invalid_client\"}");
    var resolver = new ResourceOwnerPasswordCredentialsAuthorizationResolver(httpClient);

    OAuthAuthorizationException ex =
        assertThrows(OAuthAuthorizationException.class, () -> resolver.resolve(config));
    assertEquals(401, ex.statusCode());
    assertEquals("{\"error\":\"invalid_client\"}", ex.responseBody());
  }

  @Test
  void passwordResolver_throwsOAuthAuthorizationExceptionOn500() {
    stubConfig();
    stubResponse(500, "boom");
    var resolver = new ResourceOwnerPasswordCredentialsAuthorizationResolver(httpClient);

    OAuthAuthorizationException ex =
        assertThrows(OAuthAuthorizationException.class, () -> resolver.resolve(config));
    assertEquals(500, ex.statusCode());
  }

  @Test
  void passwordResolver_returnsAccessTokenOn200() {
    stubConfig();
    stubResponse(200, "{\"access_token\":\"tok-123\"}");
    var resolver = new ResourceOwnerPasswordCredentialsAuthorizationResolver(httpClient);

    assertEquals("tok-123", resolver.resolve(config));
  }

  @Test
  void clientCredentialsResolver_throwsOAuthAuthorizationExceptionOn401() {
    stubConfig();
    when(config.isClientSecretBasic()).thenReturn(false);
    stubResponse(401, "{\"error\":\"invalid_client\"}");
    var resolver = new ClientCredentialsAuthorizationResolver(httpClient);

    OAuthAuthorizationException ex =
        assertThrows(OAuthAuthorizationException.class, () -> resolver.resolve(config));
    assertEquals(401, ex.statusCode());
    assertEquals("{\"error\":\"invalid_client\"}", ex.responseBody());
  }

  @Test
  void clientCredentialsResolver_returnsAccessTokenOn200() {
    stubConfig();
    when(config.isClientSecretBasic()).thenReturn(false);
    stubResponse(200, "{\"access_token\":\"tok-456\"}");
    var resolver = new ClientCredentialsAuthorizationResolver(httpClient);

    assertEquals("tok-456", resolver.resolve(config));
  }
}
