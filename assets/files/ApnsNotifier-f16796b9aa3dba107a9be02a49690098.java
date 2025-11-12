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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.idp.server.authentication.interactors.device.AuthenticationDeviceNotifier;
import org.idp.server.core.openid.authentication.config.AuthenticationExecutionConfig;
import org.idp.server.core.openid.identity.device.AuthenticationDevice;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.jose.JsonWebSignature;
import org.idp.server.platform.jose.JsonWebSignatureFactory;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.notification.NotificationChannel;
import org.idp.server.platform.notification.NotificationResult;
import org.idp.server.platform.notification.NotificationTemplate;

public class ApnsNotifier implements AuthenticationDeviceNotifier {

  LoggerWrapper log = LoggerWrapper.getLogger(ApnsNotifier.class);
  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();
  Map<String, JwtTokenCache> jwtTokenCache = new ConcurrentHashMap<>();
  HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).build();
  JsonWebSignatureFactory jwsFactory = new JsonWebSignatureFactory();

  private static final String PRODUCTION_URL = "https://api.push.apple.com";
  private static final String DEVELOPMENT_URL = "https://api.sandbox.push.apple.com";
  private static final long TOKEN_DURATION_SECONDS = 3600; // 1 hour

  @Override
  public NotificationChannel chanel() {
    return new NotificationChannel("apns");
  }

  @Override
  public NotificationResult notify(
      Tenant tenant, AuthenticationDevice device, AuthenticationExecutionConfig configuration) {

    try {
      log.debug("APNs notification channel called");

      if (!device.hasNotificationToken()) {
        log.debug("Device has no notification token");
        return NotificationResult.failure("apns", "Device has no notification token");
      }

      Object apnsConfigData = configuration.details().get("apns");
      if (apnsConfigData == null) {
        log.error("APNs configuration not found in details");
        return NotificationResult.failure("apns", "APNs configuration not found");
      }

      ApnsConfiguration apnsConfiguration =
          jsonConverter.read(apnsConfigData, ApnsConfiguration.class);
      String jwtToken = getOrCreateJwtToken(tenant, apnsConfiguration);

      NotificationTemplate notificationTemplate = apnsConfiguration.findTemplate("default");
      String notificationToken = device.notificationToken().value();

      String payload = createApnsPayload(notificationTemplate, tenant);
      String apnsUrl =
          (apnsConfiguration.isProduction() ? PRODUCTION_URL : DEVELOPMENT_URL)
              + "/3/device/"
              + notificationToken;

      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(apnsUrl))
              .header("Authorization", "bearer " + jwtToken)
              .header("apns-topic", apnsConfiguration.bundleId())
              .header("apns-priority", "10")
              .header("apns-push-type", "alert")
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(payload))
              .build();

      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      String apnsId = response.headers().firstValue("apns-id").orElse("unknown");

      if (response.statusCode() == 200) {
        log.info("APNs notification sent successfully, apns-id: {}", apnsId);
        return NotificationResult.success("apns", Map.of("apns-id", apnsId));
      } else {
        String errorMessage = handleApnsError(response, apnsId, tenant);
        return NotificationResult.failure("apns", errorMessage);
      }

    } catch (Exception e) {
      log.error("APNs notification failed: {}", e.getMessage());
      return NotificationResult.failure("apns", e.getMessage());
    }
  }

  String handleApnsError(HttpResponse<String> response, String apnsId, Tenant tenant) {
    try {
      int statusCode = response.statusCode();
      String responseBody = response.body();

      // Parse APNs error response
      if (Objects.nonNull(responseBody) && !responseBody.isEmpty()) {
        JsonNodeWrapper errorJson = JsonNodeWrapper.fromString(responseBody);
        String reason = errorJson.getValueOrEmptyAsString("reason");
        if (reason.isEmpty()) {
          reason = "unknown";
        }

        log.warn(
            "APNs notification failed - Status: {}, Reason: {}, APNs-ID: {}, Body: {}",
            statusCode,
            reason,
            apnsId,
            responseBody);

        // Handle specific error cases
        switch (reason) {
          case "BadDeviceToken" -> log.warn("Invalid device token");
          case "TopicDisallowed" -> log.warn("Topic not allowed");
          case "ExpiredProviderToken" -> {
            log.warn("JWT token expired, clearing cache");
            jwtTokenCache.remove(createCacheKey(tenant));
          }
        }
        return "Status: " + statusCode + ", Reason: " + reason;
      } else {
        log.warn("APNs notification failed - Status: {}, APNs-ID: {}", statusCode, apnsId);
        return "Status: " + statusCode + ", APNs-ID: " + apnsId;
      }
    } catch (Exception e) {
      log.error("Error parsing APNs error response: {}", e.getMessage());
      return "Error parsing APNs response: " + e.getMessage();
    }
  }

  String createApnsPayload(NotificationTemplate template, Tenant tenant) {
    try {
      Map<String, Object> aps = new HashMap<>();
      Map<String, String> alert = new HashMap<>();

      String title = template.optTitle("Transaction Authentication");
      String body = template.optBody("Please approve the transaction to continue.");
      String sender = template.optSender(tenant.identifierValue());

      alert.put("title", title);
      alert.put("body", body);
      aps.put("alert", alert);

      Map<String, Object> payload = new HashMap<>();
      payload.put("aps", aps);
      payload.put("sender", sender);

      return jsonConverter.write(payload);
    } catch (Exception e) {
      throw new ApnsRuntimeException("Failed to create APNs payload", e);
    }
  }

  String getOrCreateJwtToken(Tenant tenant, ApnsConfiguration config) {
    String cacheKey = createCacheKey(tenant);
    JwtTokenCache cachedToken = jwtTokenCache.get(cacheKey);

    if (cachedToken != null && !cachedToken.shouldRefresh()) {
      return cachedToken.token();
    }

    // Create new JWT token
    try {
      LocalDateTime now = SystemDateTime.now();
      LocalDateTime expiresAt = now.plusSeconds(TOKEN_DURATION_SECONDS);

      Map<String, Object> claims = new HashMap<>();
      claims.put("iss", config.teamId());
      claims.put("iat", SystemDateTime.toEpochSecond(now));

      Map<String, Object> customHeaders = new HashMap<>();
      customHeaders.put("kid", config.keyId());

      JsonWebSignature jws =
          jwsFactory.createWithAsymmetricKeyForPem(claims, customHeaders, config.keyContent());
      String token = jws.serialize();

      // Cache the new token
      jwtTokenCache.put(cacheKey, new JwtTokenCache(token, expiresAt));

      log.debug(
          "Created new JWT token for tenant: {}, expires at: {}",
          tenant.identifierValue(),
          expiresAt);

      return token;

    } catch (Exception e) {
      throw new ApnsRuntimeException("Failed to create JWT token", e);
    }
  }

  String createCacheKey(Tenant tenant) {
    return "jwt-" + tenant.identifierValue();
  }
}
