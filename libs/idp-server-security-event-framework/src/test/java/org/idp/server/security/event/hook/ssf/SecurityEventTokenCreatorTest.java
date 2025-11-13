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

import java.util.*;
import org.idp.server.platform.security.SecurityEvent;
import org.idp.server.platform.security.event.*;
import org.idp.server.platform.security.type.IpAddress;
import org.idp.server.platform.security.type.UserAgent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SecurityEventTokenCreatorTest {

  SecurityEvent securityEvent;
  SharedSignalFrameworkMetadataConfig metadataConfig;
  SharedSignalFrameworkTransmissionConfig transmissionConfig;

  @BeforeEach
  void setUp() {
    // SecurityEvent setup with tenant issuer (3 args constructor)
    SecurityEventTenant tenant =
        new SecurityEventTenant("tenant-id", "https://tenant.example.com", "Test Tenant");
    SecurityEventClient client = new SecurityEventClient("client-id", "client-123");
    // SecurityEventUser (5 args constructor: sub, name, exSub, email, phoneNumber)
    SecurityEventUser user =
        new SecurityEventUser("user-123", "Test User", "user-ex-123", "test@example.com", null);
    SecurityEventDetail detail = new SecurityEventDetail(Map.of("event", "test"));

    securityEvent =
        new SecurityEventBuilder()
            .add(
                new org.idp.server.platform.security.event.SecurityEventType(
                    "authentication_success"))
            .add(new SecurityEventDescription("User authenticated successfully"))
            .add(tenant)
            .add(client)
            .add(user)
            .add(new IpAddress("192.168.1.1"))
            .add(new UserAgent("Mozilla/5.0"))
            .add(detail)
            .build();

    // SSF metadata config with SSF issuer (different from tenant issuer)
    metadataConfig = new SharedSignalFrameworkMetadataConfig();
    metadataConfig.specVersion = "1.0";
    metadataConfig.issuer = "https://ssf.example.com"; // SSF issuer (SET発行者)

    // Transmission config (use url, not endpoint)
    transmissionConfig = new SharedSignalFrameworkTransmissionConfig();
    transmissionConfig.url = "https://receiver.example.com/ssf";
    transmissionConfig.kid = "test-key-1";
    transmissionConfig.securityEventTypeIdentifier =
        "https://schemas.openid.net/secevent/caep/event-type/session-revoked";
    transmissionConfig.securityEventTokenHeaders = Map.of("typ", "secevent+jwt");
    transmissionConfig.securityEventTokenAdditionalPayloadMappingRules = new ArrayList<>();
  }

  @Test
  void create_shouldUseMetadataConfigIssuerNotTenantIssuer_viaConvert() {
    // Test via convert() method which doesn't require valid JWT signing
    SecurityEventTokenCreator creator =
        new SecurityEventTokenCreator(securityEvent, metadataConfig, transmissionConfig);

    SecurityEventTokenEntity entity = creator.convert();

    assertNotNull(entity);
    // RFC 8417: iss claim should contain the SET issuer (SSF metadata config issuer)
    assertEquals(
        "https://ssf.example.com",
        entity.issuerValue(),
        "issuer should use metadataConfig.issuer (SSF issuer), not tenant issuer");
    // Without Stream Configuration, aud should be empty (RFC 8417 allows omitting aud)
    assertTrue(
        entity.audience().isEmpty(), "aud should be empty when Stream Configuration is not set");
  }

  @Test
  void convert_shouldIncludeTenantIssuerInSubjectClaim() {
    SecurityEventTokenCreator creator =
        new SecurityEventTokenCreator(securityEvent, metadataConfig, transmissionConfig);

    SecurityEventTokenEntity entity = creator.convert();

    assertNotNull(entity);
    Map<String, Object> eventMap = entity.eventAsMap();

    @SuppressWarnings("unchecked")
    Map<String, Object> eventDetail =
        (Map<String, Object>)
            eventMap.get("https://schemas.openid.net/secevent/caep/event-type/session-revoked");

    assertNotNull(eventDetail, "Event detail should be present");

    @SuppressWarnings("unchecked")
    Map<String, Object> subject = (Map<String, Object>) eventDetail.get("subject");

    assertNotNull(subject, "Subject should be present");
    assertEquals("iss_sub", subject.get("format"), "Subject format should be iss_sub");

    // SecurityEventSubject.toMap() flattens payload into the same map
    // RFC 8417: subject.iss should be the original token issuer (tenant issuer)
    assertEquals(
        "https://tenant.example.com",
        subject.get("iss"),
        "subject.iss should be the tenant issuer (original token issuer)");
    assertEquals("user-123", subject.get("sub"), "subject.sub should be user sub");
  }

  @Test
  void convert_shouldUseStreamConfigurationAudWhenConfigured() {
    // Setup stream configuration with specific aud
    StreamConfiguration streamConfig = new StreamConfiguration();
    streamConfig.aud = List.of("stream-receiver-123");
    metadataConfig.streamConfiguration = streamConfig;

    SecurityEventTokenCreator creator =
        new SecurityEventTokenCreator(securityEvent, metadataConfig, transmissionConfig);

    SecurityEventTokenEntity entity = creator.convert();

    assertNotNull(entity);
    // Should use stream configuration's aud, not client ID
    assertEquals(
        List.of("stream-receiver-123"),
        entity.audience(),
        "aud should use streamConfiguration.aud() when configured");
    assertEquals(
        "stream-receiver-123",
        entity.audienceValue(),
        "audienceValue() should return single string when list has one element");
  }

  @Test
  void convert_shouldOmitAudWhenStreamConfigNotSet() {
    // No stream configuration set
    metadataConfig.streamConfiguration = null;

    SecurityEventTokenCreator creator =
        new SecurityEventTokenCreator(securityEvent, metadataConfig, transmissionConfig);

    SecurityEventTokenEntity entity = creator.convert();

    assertNotNull(entity);
    // Should omit aud when Stream Configuration is not set
    assertTrue(
        entity.audience().isEmpty(), "aud should be empty when streamConfiguration is not set");
    assertFalse(entity.hasAudience(), "hasAudience() should return false");
  }

  @Test
  void convert_shouldOmitAudWhenStreamConfigAudEmpty() {
    // Stream configuration exists but aud is empty
    StreamConfiguration streamConfig = new StreamConfiguration();
    streamConfig.aud = new ArrayList<>();
    metadataConfig.streamConfiguration = streamConfig;

    SecurityEventTokenCreator creator =
        new SecurityEventTokenCreator(securityEvent, metadataConfig, transmissionConfig);

    SecurityEventTokenEntity entity = creator.convert();

    assertNotNull(entity);
    // Should omit aud when Stream Configuration aud is empty
    assertTrue(
        entity.audience().isEmpty(), "aud should be empty when streamConfiguration.aud is empty");
    assertFalse(entity.hasAudience(), "hasAudience() should return false");
  }

  @Test
  void convert_audIsOptionalPerRFC8417() {
    // Setup: No stream configuration and no client ID
    metadataConfig.streamConfiguration = null;
    SecurityEventClient emptyClient = new SecurityEventClient(null, null);
    SecurityEventTenant tenant =
        new SecurityEventTenant("tenant-id", "https://tenant.example.com", "Test Tenant");
    SecurityEventUser user =
        new SecurityEventUser("user-123", "Test User", "user-ex-123", "test@example.com", null);
    SecurityEventDetail detail = new SecurityEventDetail(Map.of("event", "test"));

    SecurityEvent eventWithoutClient =
        new SecurityEventBuilder()
            .add(
                new org.idp.server.platform.security.event.SecurityEventType(
                    "authentication_success"))
            .add(new SecurityEventDescription("User authenticated successfully"))
            .add(tenant)
            .add(emptyClient)
            .add(user)
            .add(new IpAddress("192.168.1.1"))
            .add(new UserAgent("Mozilla/5.0"))
            .add(detail)
            .build();

    SecurityEventTokenCreator creator =
        new SecurityEventTokenCreator(eventWithoutClient, metadataConfig, transmissionConfig);

    SecurityEventTokenEntity entity = creator.convert();

    assertNotNull(entity);
    // RFC 8417: aud is RECOMMENDED (not REQUIRED)
    // When no audience is available, clientIdValue should be null or empty
    assertTrue(
        entity.audience() == null || entity.audience().isEmpty(),
        "aud can be omitted per RFC 8417 Section 2.2 (RECOMMENDED, not REQUIRED)");
  }
}
