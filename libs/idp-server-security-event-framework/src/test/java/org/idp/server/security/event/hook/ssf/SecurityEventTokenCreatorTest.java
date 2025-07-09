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

package org.idp.server.security.event.hook.ssf;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.idp.server.basic.jose.JoseContext;
import org.idp.server.basic.jose.JoseHandler;
import org.idp.server.basic.jose.JoseInvalidException;
import org.idp.server.basic.jose.JsonWebTokenClaims;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.junit.jupiter.api.Test;

public class SecurityEventTokenCreatorTest {

  @Test
  public void createSET_AccountEnabled() throws JoseInvalidException {
    String issuer = "http://localhost:8080/67e7eae6-62b0-4500-9eff-87459f63fc66";
    String requestedClientId = "test-client-id";
    SecurityEventType type = SecurityEventType.AccountEnabled;
    SecurityEventSubject subject =
        new SecurityEventSubject(
            SecuritySubjectFormat.iss_sub,
            new SecurityEventSubjectPayload(
                Map.of("iss", issuer, "sub", "53f596fc-6aca-4304-ac35-a94f11df2506")));
    SecurityEventPayload payload = new SecurityEventPayload(Map.of("ex_sub", "1001234567"));
    SharedSecurityEvent sharedSecurityEvent = new SharedSecurityEvent(type, subject, payload);
    SecurityEventTokenEntity securityEventTokenEntity =
        new SecurityEventTokenEntity(issuer, requestedClientId, sharedSecurityEvent);

    String privateKey =
        """
                {\n    \"kty\": \"EC\",\n    \"d\": \"_9Ki4nmPFljpGneu1bZeAVxHoJiOnt7Cmr98qSI9SQI\",\n    \"use\": \"sig\",\n    \"crv\": \"P-256\",\n    \"kid\": \"ssf-debug\",\n    \"x\": \"nitxvSyqpZEhGXT-oJ8CJD_Pr3feMVFAO43d8TlHk1w\",\n    \"y\": \"xA6Slvr4rRnF53TNaIdeRsw9v655xGlHPD3YNGy_CMY\",\n    \"alg\": \"ES256\"\n}
                """;

    SecurityEventTokenCreator securityEventTokenCreator =
        new SecurityEventTokenCreator(securityEventTokenEntity, privateKey);

    SecurityEventToken securityEventToken = securityEventTokenCreator.create();

    System.out.println(securityEventToken.value());
    assertNotNull(securityEventToken.value());

    String publicJwks =
        """
                {
                  "keys": [
                  {\n    \"kty\": \"EC\",\n    \"use\": \"sig\",\n    \"crv\": \"P-256\",\n    \"kid\": \"ssf-debug\",\n    \"x\": \"nitxvSyqpZEhGXT-oJ8CJD_Pr3feMVFAO43d8TlHk1w\",\n    \"y\": \"xA6Slvr4rRnF53TNaIdeRsw9v655xGlHPD3YNGy_CMY\",\n    \"alg\": \"ES256\"\n}
                  ]
                }
                """;

    JoseHandler joseHandler = new JoseHandler();
    JoseContext joseContext = joseHandler.handle(securityEventToken.value(), publicJwks, "", "");

    joseContext.verifySignature();

    JsonWebTokenClaims claims = joseContext.claims();
    assertEquals(issuer, claims.getIss());
    assertNotNull(claims.getAud());
    assertNotNull(claims.getIat());
    assertNotNull(claims.getJti());

    JsonNodeWrapper events = JsonNodeWrapper.fromObject(claims.toMap().get("events"));
    assertNotNull(events);
    JsonNodeWrapper accountEnabled =
        events.getValueAsJsonNode(
            "https://schemas.openid.net/secevent/risc/event-type/account-enabled");
    assertNotNull(accountEnabled);
    assertEquals("1001234567", accountEnabled.getValueOrEmptyAsString("ex_sub"));

    JsonNodeWrapper subjectNode = accountEnabled.getValueAsJsonNode("subject");
    assertEquals("iss_sub", subjectNode.getValueOrEmptyAsString("format"));
    assertEquals(
        "53f596fc-6aca-4304-ac35-a94f11df2506", subjectNode.getValueOrEmptyAsString("sub"));
    assertEquals(issuer, subjectNode.getValueOrEmptyAsString("iss"));
  }

  @Test
  public void createSET_AccountDisabled() throws JoseInvalidException {
    String issuer = "http://localhost:8080/67e7eae6-62b0-4500-9eff-87459f63fc66";
    String requestedClientId = "test-client-id";
    SecurityEventType type = SecurityEventType.AccountDisabled;
    SecurityEventSubject subject =
        new SecurityEventSubject(
            SecuritySubjectFormat.iss_sub,
            new SecurityEventSubjectPayload(
                Map.of("iss", issuer, "sub", "53f596fc-6aca-4304-ac35-a94f11df2506")));
    SecurityEventPayload payload = new SecurityEventPayload(Map.of("ex_sub", "1001234567"));
    SharedSecurityEvent sharedSecurityEvent = new SharedSecurityEvent(type, subject, payload);
    SecurityEventTokenEntity securityEventTokenEntity =
        new SecurityEventTokenEntity(issuer, requestedClientId, sharedSecurityEvent);

    String privateKey =
        """
                {\n    \"kty\": \"EC\",\n    \"d\": \"_9Ki4nmPFljpGneu1bZeAVxHoJiOnt7Cmr98qSI9SQI\",\n    \"use\": \"sig\",\n    \"crv\": \"P-256\",\n    \"kid\": \"ssf-debug\",\n    \"x\": \"nitxvSyqpZEhGXT-oJ8CJD_Pr3feMVFAO43d8TlHk1w\",\n    \"y\": \"xA6Slvr4rRnF53TNaIdeRsw9v655xGlHPD3YNGy_CMY\",\n    \"alg\": \"ES256\"\n}
                """;

    SecurityEventTokenCreator securityEventTokenCreator =
        new SecurityEventTokenCreator(securityEventTokenEntity, privateKey);

    SecurityEventToken securityEventToken = securityEventTokenCreator.create();

    System.out.println(securityEventToken.value());
    assertNotNull(securityEventToken.value());

    String publicJwks =
        """
                {
                  "keys": [
                  {\n    \"kty\": \"EC\",\n    \"use\": \"sig\",\n    \"crv\": \"P-256\",\n    \"kid\": \"ssf-debug\",\n    \"x\": \"nitxvSyqpZEhGXT-oJ8CJD_Pr3feMVFAO43d8TlHk1w\",\n    \"y\": \"xA6Slvr4rRnF53TNaIdeRsw9v655xGlHPD3YNGy_CMY\",\n    \"alg\": \"ES256\"\n}
                  ]
                }
                """;

    JoseHandler joseHandler = new JoseHandler();
    JoseContext joseContext = joseHandler.handle(securityEventToken.value(), publicJwks, "", "");

    joseContext.verifySignature();

    JsonWebTokenClaims claims = joseContext.claims();
    assertEquals(issuer, claims.getIss());
    assertNotNull(claims.getAud());
    assertNotNull(claims.getIat());
    assertNotNull(claims.getJti());

    JsonNodeWrapper events = JsonNodeWrapper.fromObject(claims.toMap().get("events"));
    assertNotNull(events);
    JsonNodeWrapper accountEnabled =
        events.getValueAsJsonNode(
            "https://schemas.openid.net/secevent/risc/event-type/account-disabled");
    assertNotNull(accountEnabled);
    assertEquals("1001234567", accountEnabled.getValueOrEmptyAsString("ex_sub"));

    JsonNodeWrapper subjectNode = accountEnabled.getValueAsJsonNode("subject");
    assertEquals("iss_sub", subjectNode.getValueOrEmptyAsString("format"));
    assertEquals(
        "53f596fc-6aca-4304-ac35-a94f11df2506", subjectNode.getValueOrEmptyAsString("sub"));
    assertEquals(issuer, subjectNode.getValueOrEmptyAsString("iss"));
  }
}
