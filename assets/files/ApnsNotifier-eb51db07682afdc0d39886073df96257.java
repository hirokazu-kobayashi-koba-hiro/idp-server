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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.idp.server.authentication.interactors.device.AuthenticationDeviceNotifier;
import org.idp.server.core.openid.authentication.config.AuthenticationExecutionConfig;
import org.idp.server.core.openid.identity.device.AuthenticationDevice;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.http.HttpRequestResult;
import org.idp.server.platform.http.HttpRetryConfiguration;
import org.idp.server.platform.http.HttpRetryStrategy;
import org.idp.server.platform.jose.JsonWebSignature;
import org.idp.server.platform.jose.JsonWebSignatureFactory;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.notification.NotificationChannel;
import org.idp.server.platform.notification.NotificationResult;
import org.idp.server.platform.notification.NotificationTemplate;

/**
 * {@link AuthenticationDeviceNotifier} implementation for Apple Push Notification service (APNs).
 *
 * <p>Pushes authentication challenges (CIBA / FIDO-UAF device notifications) to iOS devices over
 * the APNs HTTP/2 API ({@code POST /3/device/{token}}).
 *
 * <h3>Connection &amp; authentication</h3>
 *
 * <ul>
 *   <li><b>HTTP/2 only</b>: APNs requires HTTP/2, so a dedicated {@link HttpClient} pinned to
 *       {@code HTTP_2} is used (the shared platform client is HTTP/1.1 and cannot be reused here).
 *   <li><b>Provider token</b>: requests are authenticated with a JWT (ES256) bearer "provider
 *       token" built from the tenant's APNs key, cached per tenant and refreshed on expiry (see
 *       {@link #getOrCreateJwtToken}).
 * </ul>
 *
 * <h3>Concurrency &amp; retry (#1539)</h3>
 *
 * <p>This notifier is a shared instance, so all tenants and concurrent CIBA requests multiplex over
 * the same APNs HTTP/2 connection pool. Under a burst that oversubscribes a reused connection's
 * {@code MAX_CONCURRENT_STREAMS}, {@code HttpClient#send} can throw the transient {@code
 * IOException("too many concurrent streams")}. To avoid dropping notifications, {@link #send} maps
 * that (and other transient failures) to a retryable status and retry/backoff is delegated to the
 * platform {@link HttpRetryStrategy} — by default a single 100ms retry, tunable via {@link
 * ApnsConfiguration#retryConfiguration()}.
 *
 * <h3>Security</h3>
 *
 * <p>The device {@code credential_payload} / provider key are never logged; only non-sensitive
 * metadata (status, APNs reason, apns-id) is emitted on failures.
 */
public class ApnsNotifier implements AuthenticationDeviceNotifier {

  LoggerWrapper log = LoggerWrapper.getLogger(ApnsNotifier.class);
  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();
  Map<String, JwtTokenCache> jwtTokenCache = new ConcurrentHashMap<>();
  HttpClient httpClient =
      HttpClient.newBuilder()
          .version(HttpClient.Version.HTTP_2)
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();
  JsonWebSignatureFactory jwsFactory = new JsonWebSignatureFactory();
  // Retry/backoff is delegated to the shared platform strategy. Package-private for tests. (#1539)
  HttpRetryStrategy retryStrategy = new HttpRetryStrategy();

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

      return sendWithRetry(tenant, request, apnsConfiguration.retryConfiguration());

    } catch (Exception e) {
      log.error("APNs notification failed: {}", e.getMessage());
      return NotificationResult.failure("apns", e.getMessage());
    }
  }

  /**
   * Sends the request, delegating retry/backoff to the shared {@link HttpRetryStrategy}. Transient
   * failures — an {@link IOException} (e.g. the HTTP/2 "too many concurrent streams" thrown when a
   * burst oversubscribes the shared connection, mapped to 502 in {@link #send}) and APNs 429 / 5xx
   * — are retried per {@code retryConfig}; permanent 4xx (BadDeviceToken, etc.) are returned
   * without retry. The retry budget is bounded ({@link ApnsConfiguration#retryConfiguration()}
   * defaults to a single 100ms retry) because CIBA backchannel requests wait on this send
   * synchronously. (#1539)
   */
  NotificationResult sendWithRetry(
      Tenant tenant, HttpRequest request, HttpRetryConfiguration retryConfig) {
    HttpRequestResult result = retryStrategy.executeWithRetry(request, retryConfig, this::send);

    String apnsId = firstHeader(result.headers(), "apns-id");
    if (result.statusCode() == 200) {
      log.info("APNs notification sent successfully, apns-id: {}", apnsId);
      return NotificationResult.success("apns", Map.of("apns-id", apnsId));
    }

    return NotificationResult.failure("apns", handleApnsError(result, apnsId, tenant));
  }

  /**
   * Performs a single send. Checked exceptions are mapped to retryable status results (502/503) so
   * the status-code-based {@link HttpRetryStrategy} can retry them — notably the HTTP/2 "too many
   * concurrent streams" {@link IOException}. (#1539)
   */
  HttpRequestResult send(HttpRequest request) {
    try {
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      return new HttpRequestResult(
          response.statusCode(), response.headers().map(), parseBody(response.body()));
    } catch (IOException e) {
      log.warn("APNs send I/O error (mapped to 502 for retry): {}", e.getMessage());
      return new HttpRequestResult(502, Map.of(), reasonBody(e.getMessage()));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return new HttpRequestResult(503, Map.of(), reasonBody("interrupted: " + e.getMessage()));
    }
  }

  private JsonNodeWrapper parseBody(String body) {
    if (body == null || body.isEmpty()) {
      return JsonNodeWrapper.fromMap(Map.of());
    }
    try {
      return JsonNodeWrapper.fromString(body);
    } catch (Exception e) {
      log.warn("Failed to parse APNs response body as JSON: {}", e.getMessage());
      return JsonNodeWrapper.fromMap(Map.of());
    }
  }

  private JsonNodeWrapper reasonBody(String reason) {
    return JsonNodeWrapper.fromMap(Map.of("reason", reason == null ? "unknown" : reason));
  }

  private String firstHeader(Map<String, List<String>> headers, String name) {
    if (headers != null) {
      for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
        if (name.equalsIgnoreCase(entry.getKey())
            && entry.getValue() != null
            && !entry.getValue().isEmpty()) {
          return entry.getValue().get(0);
        }
      }
    }
    return "unknown";
  }

  String handleApnsError(HttpRequestResult result, String apnsId, Tenant tenant) {
    int statusCode = result.statusCode();
    JsonNodeWrapper body = result.body();

    if (body != null && body.exists() && body.contains("reason")) {
      String reason = body.getValueOrEmptyAsString("reason");
      if (reason.isEmpty()) {
        reason = "unknown";
      }

      log.warn(
          "APNs notification failed - Status: {}, Reason: {}, APNs-ID: {}",
          statusCode,
          reason,
          apnsId);

      // Handle specific error cases
      switch (reason) {
        case "BadDeviceToken" -> log.warn("Invalid device token");
        case "TopicDisallowed" -> log.warn("Topic not allowed");
        case "ExpiredProviderToken" -> {
          log.warn("JWT token expired, clearing cache");
          jwtTokenCache.remove(createCacheKey(tenant));
        }
        default -> {}
      }
      return "Status: " + statusCode + ", Reason: " + reason;
    }

    log.warn("APNs notification failed - Status: {}, APNs-ID: {}", statusCode, apnsId);
    return "Status: " + statusCode + ", APNs-ID: " + apnsId;
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
