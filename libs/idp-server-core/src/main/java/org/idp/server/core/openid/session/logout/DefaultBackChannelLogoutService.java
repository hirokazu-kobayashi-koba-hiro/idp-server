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

package org.idp.server.core.openid.session.logout;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.openid.session.ClientSessionIdentifier;
import org.idp.server.platform.jose.JoseContext;
import org.idp.server.platform.jose.JoseHandler;
import org.idp.server.platform.jose.JsonWebSignature;
import org.idp.server.platform.jose.JsonWebSignatureFactory;
import org.idp.server.platform.log.LoggerWrapper;

public class DefaultBackChannelLogoutService implements BackChannelLogoutService {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(DefaultBackChannelLogoutService.class);

  private final LogoutTokenJtiRepository jtiRepository;
  private final JsonWebSignatureFactory jsonWebSignatureFactory;
  private final JoseHandler joseHandler;
  private final HttpClient httpClient;

  public DefaultBackChannelLogoutService(
      LogoutTokenJtiRepository jtiRepository, HttpClient httpClient) {
    this.jtiRepository = jtiRepository;
    this.jsonWebSignatureFactory = new JsonWebSignatureFactory();
    this.joseHandler = new JoseHandler();
    this.httpClient = httpClient;
  }

  /**
   * Creates a default HttpClient configured for back-channel logout.
   *
   * @return a configured HttpClient instance
   */
  public static HttpClient createDefaultHttpClient() {
    return HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();
  }

  @Override
  public LogoutToken generateLogoutToken(
      String issuer, String clientId, String sub, ClientSessionIdentifier sid) {
    return LogoutToken.create(issuer, clientId, sub, sid);
  }

  @Override
  public String encodeLogoutToken(
      LogoutToken token, String signingAlgorithm, String signingKeyJwks) {
    try {
      Map<String, Object> claims = token.toClaimsMap();
      Map<String, Object> customHeaders = new HashMap<>();
      JsonWebSignature jws =
          jsonWebSignatureFactory.createWithAsymmetricKeyByAlgorithm(
              claims, customHeaders, signingKeyJwks, signingAlgorithm);
      return jws.serialize();
    } catch (Exception e) {
      log.error("Failed to encode logout token", e);
      throw new RuntimeException("Failed to encode logout token", e);
    }
  }

  @Override
  public LogoutTokenValidationResult validateLogoutToken(
      String token, String expectedIssuer, String expectedAudience, String publicKeyJwks) {
    try {
      JoseContext context = joseHandler.handle(token, publicKeyJwks, null, null);

      // Verify signature
      context.verifySignature();

      Map<String, Object> claims = context.claimsAsMap();
      LogoutToken logoutToken = LogoutToken.fromClaimsMap(claims);

      // Validate issuer
      if (!expectedIssuer.equals(logoutToken.iss())) {
        return LogoutTokenValidationResult.failure("invalid_issuer", "Invalid issuer");
      }

      // Validate audience
      if (!expectedAudience.equals(logoutToken.aud())) {
        return LogoutTokenValidationResult.failure("invalid_audience", "Invalid audience");
      }

      // Validate events claim
      if (!logoutToken.hasBackchannelLogoutEvent()) {
        return LogoutTokenValidationResult.failure(
            "invalid_token", "Missing backchannel-logout event");
      }

      // Validate sub or sid exists
      if (!logoutToken.hasSubOrSid()) {
        return LogoutTokenValidationResult.failure(
            "invalid_token", "Either sub or sid must be present");
      }

      // Check JTI not already used
      if (isJtiUsed(logoutToken.jti())) {
        return LogoutTokenValidationResult.failure(
            "replay_attack", "JTI already used (replay attack)");
      }

      return LogoutTokenValidationResult.success(logoutToken);
    } catch (Exception e) {
      log.error("Failed to validate logout token", e);
      return LogoutTokenValidationResult.failure(
          "validation_error", "Token validation failed: " + e.getMessage());
    }
  }

  @Override
  public boolean isJtiUsed(String jti) {
    return jtiRepository.isUsed(jti);
  }

  @Override
  public void markJtiUsed(String jti, long ttlSeconds) {
    jtiRepository.markUsed(jti, ttlSeconds);
  }

  @Override
  public BackChannelNotificationResult sendNotification(
      String backchannelLogoutUri, String logoutToken, int timeoutMs) {
    try {
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(backchannelLogoutUri))
              .timeout(Duration.ofMillis(timeoutMs))
              .header("Content-Type", "application/x-www-form-urlencoded")
              .POST(HttpRequest.BodyPublishers.ofString("logout_token=" + logoutToken))
              .build();

      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      int statusCode = response.statusCode();
      if (statusCode == 200 || statusCode == 204) {
        log.info(
            "Back-channel logout notification sent successfully to {}, status: {}",
            backchannelLogoutUri,
            statusCode);
        return BackChannelNotificationResult.success(statusCode);
      } else {
        log.warn(
            "Back-channel logout notification failed for {}, status: {}, body: {}",
            backchannelLogoutUri,
            statusCode,
            response.body());
        return BackChannelNotificationResult.failure(
            statusCode, "HTTP " + statusCode + ": " + response.body());
      }
    } catch (IOException e) {
      log.error("Network error sending back-channel logout to {}", backchannelLogoutUri, e);
      return BackChannelNotificationResult.failure(0, "Network error: " + e.getMessage());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.error("Interrupted while sending back-channel logout to {}", backchannelLogoutUri, e);
      return BackChannelNotificationResult.failure(0, "Request interrupted");
    } catch (Exception e) {
      log.error("Unexpected error sending back-channel logout to {}", backchannelLogoutUri, e);
      return BackChannelNotificationResult.failure(0, "Unexpected error: " + e.getMessage());
    }
  }
}
