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

import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;
import org.idp.server.platform.exception.InvalidConfigurationException;
import org.idp.server.platform.json.JsonConverter;
import org.junit.jupiter.api.Test;

class OAuthAuthorizationTokenRequestFactoryTest {

  OAuthAuthorizationTokenRequestFactory factory = new OAuthAuthorizationTokenRequestFactory();

  static OAuthAuthorizationConfiguration config(Map<String, Object> values) {
    return JsonConverter.snakeCaseInstance().read(values, OAuthAuthorizationConfiguration.class);
  }

  static Map<String, Object> passwordGrant(String clientAuthenticationType) {
    Map<String, Object> values = new java.util.HashMap<>();
    values.put("type", "password");
    values.put("token_endpoint", "https://idp.example.com/token");
    values.put("client_id", "client-1");
    values.put("client_secret", "0123456789abcdef0123456789abcdef");
    values.put("username", "alice");
    values.put("password", "pw");
    values.put("scope", "read");
    if (clientAuthenticationType != null) {
      values.put("client_authentication_type", clientAuthenticationType);
    }
    return values;
  }

  /** The form body of a request, as the receiving server would read it. */
  static String body(HttpRequest request) {
    List<ByteBuffer> buffers = new ArrayList<>();
    request
        .bodyPublisher()
        .orElseThrow()
        .subscribe(
            new Flow.Subscriber<>() {
              public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
              }

              public void onNext(ByteBuffer item) {
                buffers.add(item);
              }

              public void onError(Throwable throwable) {}

              public void onComplete() {}
            });
    StringBuilder builder = new StringBuilder();
    buffers.forEach(buffer -> builder.append(StandardCharsets.UTF_8.decode(buffer)));
    return builder.toString();
  }

  @Test
  void aPasswordGrantUnderClientSecretBasicSendsTheAuthorizationHeader() {
    // It used to send neither the header nor the secret: the password resolver never added it.
    HttpRequest request = factory.create(config(passwordGrant("client_secret_basic")));

    assertTrue(request.headers().firstValue("Authorization").orElse("").startsWith("Basic "));
    assertFalse(body(request).contains("client_secret="));
    assertTrue(body(request).contains("username=alice"));
  }

  @Test
  void sendsOnlyTheClientIdWithoutAClientAuthenticationType() {
    HttpRequest request = factory.create(config(passwordGrant(null)));

    assertTrue(request.headers().firstValue("Authorization").isEmpty());
    assertTrue(body(request).contains("client_id=client-1"));
    assertFalse(body(request).contains("client_secret="));
  }

  @Test
  void sendsAClientAssertionUnderClientSecretJwt() {
    HttpRequest request = factory.create(config(passwordGrant("client_secret_jwt")));

    String body = body(request);
    assertTrue(body.contains("client_assertion="));
    assertTrue(body.contains("client_assertion_type="));
    assertFalse(body.contains("client_secret="));
  }

  @Test
  void refusesAClientAuthenticationTypeItDoesNotKnow() {
    assertThrows(
        InvalidConfigurationException.class,
        () -> factory.create(config(passwordGrant("client_secret_basik"))));
  }
}
