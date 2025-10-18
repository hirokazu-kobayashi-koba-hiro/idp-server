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

package org.idp.server.notification.push.apns;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.net.http.HttpResponse;
import org.idp.server.platform.dependency.protocol.AuthorizationProvider;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.multi_tenancy.tenant.*;
import org.idp.server.platform.multi_tenancy.tenant.config.CorsConfiguration;
import org.idp.server.platform.multi_tenancy.tenant.config.SessionConfiguration;
import org.idp.server.platform.multi_tenancy.tenant.config.UIConfiguration;
import org.idp.server.platform.multi_tenancy.tenant.policy.TenantIdentityPolicy;
import org.idp.server.platform.notification.NotificationChannel;
import org.idp.server.platform.notification.NotificationTemplate;
import org.idp.server.platform.security.event.SecurityEventUserAttributeConfiguration;
import org.idp.server.platform.security.log.SecurityEventLogConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ApnsNotifierTest {

  private ApnsNotifier apnsNotifier;

  @BeforeEach
  void setUp() {
    apnsNotifier = new ApnsNotifier();
  }

  @Test
  void testChannel() {
    NotificationChannel channel = apnsNotifier.chanel();
    assertEquals("apns", channel.name());
  }

  @Test
  void testCreateApnsPayload() {
    // Create tenant with required fields
    Tenant tenant =
        new Tenant(
            new TenantIdentifier("test-tenant"),
            new TenantName("Test Tenant"),
            TenantType.PUBLIC,
            new TenantDomain("test.example.com"),
            new AuthorizationProvider("idp-server"),
            new TenantAttributes(),
            new UIConfiguration(),
            new CorsConfiguration(),
            new SessionConfiguration(),
            new SecurityEventLogConfiguration(),
            new SecurityEventUserAttributeConfiguration(),
            TenantIdentityPolicy.defaultPolicy());

    // Create notification template
    NotificationTemplate template =
        new NotificationTemplate("Test Sender", "Test Title", "Test Body");

    String payload = apnsNotifier.createApnsPayload(template, tenant);

    // Parse and validate JSON structure
    JsonNodeWrapper payloadJson = JsonNodeWrapper.fromString(payload);

    assertTrue(payloadJson.contains("aps"));
    assertTrue(payloadJson.contains("sender"));

    JsonNodeWrapper aps = payloadJson.getNode("aps");
    assertTrue(aps.contains("alert"));
    assertTrue(aps.contains("content-available"));

    JsonNodeWrapper alert = aps.getNode("alert");
    assertTrue(alert.contains("title"));
    assertTrue(alert.contains("body"));

    assertEquals(1, aps.getValueAsInt("content-available"));
    assertEquals("Test Title", alert.getValueOrEmptyAsString("title"));
    assertEquals("Test Body", alert.getValueOrEmptyAsString("body"));
    assertEquals("Test Sender", payloadJson.getValueOrEmptyAsString("sender"));
  }

  @Test
  void testCreateApnsPayloadWithEmptyTemplate() {
    // Create tenant
    Tenant tenant =
        new Tenant(
            new TenantIdentifier("test-tenant"),
            new TenantName("Test Tenant"),
            TenantType.PUBLIC,
            new TenantDomain("test.example.com"),
            new AuthorizationProvider("idp-server"),
            new TenantAttributes(),
            new UIConfiguration(),
            new CorsConfiguration(),
            new SessionConfiguration(),
            new SecurityEventLogConfiguration(),
            new SecurityEventUserAttributeConfiguration(),
            TenantIdentityPolicy.defaultPolicy());

    NotificationTemplate emptyTemplate = new NotificationTemplate();
    String payload = apnsNotifier.createApnsPayload(emptyTemplate, tenant);

    // Should not throw exception and should create valid JSON with defaults
    JsonNodeWrapper payloadJson = JsonNodeWrapper.fromString(payload);
    assertTrue(payloadJson.contains("aps"));
    assertTrue(payloadJson.contains("sender"));

    JsonNodeWrapper alert = payloadJson.getNode("aps").getNode("alert");
    assertEquals("Transaction Authentication", alert.getValueOrEmptyAsString("title"));
    assertEquals(
        "Please approve the transaction to continue.", alert.getValueOrEmptyAsString("body"));
  }

  @Test
  void testCacheKeyGeneration() {
    Tenant tenant =
        new Tenant(
            new TenantIdentifier("test-tenant-123"),
            new TenantName("Test Tenant"),
            TenantType.PUBLIC,
            new TenantDomain("test.example.com"),
            new AuthorizationProvider("idp-server"),
            new TenantAttributes(),
            new UIConfiguration(),
            new CorsConfiguration(),
            new SessionConfiguration(),
            new SecurityEventLogConfiguration(),
            new SecurityEventUserAttributeConfiguration(),
            TenantIdentityPolicy.defaultPolicy());

    String cacheKey = apnsNotifier.createCacheKey(tenant);

    assertEquals("jwt-test-tenant-123", cacheKey);
  }

  @Test
  void testHandleApnsErrorWithBadDeviceToken() {
    Tenant tenant =
        new Tenant(
            new TenantIdentifier("test-tenant"),
            new TenantName("Test Tenant"),
            TenantType.PUBLIC,
            new TenantDomain("test.example.com"),
            new AuthorizationProvider("idp-server"),
            new TenantAttributes(),
            new UIConfiguration(),
            new CorsConfiguration(),
            new SessionConfiguration(),
            new SecurityEventLogConfiguration(),
            new SecurityEventUserAttributeConfiguration(),
            TenantIdentityPolicy.defaultPolicy());

    HttpResponse<String> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(400);
    when(response.body()).thenReturn("{\"reason\": \"BadDeviceToken\"}");

    // Should not throw exception
    assertDoesNotThrow(() -> apnsNotifier.handleApnsError(response, "test-apns-id", tenant));
  }

  @Test
  void testHandleApnsErrorWithExpiredToken() {
    Tenant tenant =
        new Tenant(
            new TenantIdentifier("test-tenant"),
            new TenantName("Test Tenant"),
            TenantType.PUBLIC,
            new TenantDomain("test.example.com"),
            new AuthorizationProvider("idp-server"),
            new TenantAttributes(),
            new UIConfiguration(),
            new CorsConfiguration(),
            new SessionConfiguration(),
            new SecurityEventLogConfiguration(),
            new SecurityEventUserAttributeConfiguration(),
            TenantIdentityPolicy.defaultPolicy());

    HttpResponse<String> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(403);
    when(response.body()).thenReturn("{\"reason\": \"ExpiredProviderToken\"}");

    assertDoesNotThrow(() -> apnsNotifier.handleApnsError(response, "test-apns-id", tenant));
  }
}
