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

import java.util.ArrayList;
import java.util.List;
import org.idp.server.core.openid.session.ClientSession;
import org.idp.server.core.openid.session.ClientSessions;
import org.idp.server.core.openid.session.OIDCSessionManager;
import org.idp.server.core.openid.session.OPSessionIdentifier;
import org.idp.server.core.openid.session.TerminationReason;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class LogoutOrchestrator {

  private static final LoggerWrapper log = LoggerWrapper.getLogger(LogoutOrchestrator.class);
  private static final int DEFAULT_BACKCHANNEL_TIMEOUT_MS = 5000;

  private final OIDCSessionManager sessionManager;
  private final BackChannelLogoutService backChannelLogoutService;
  private final FrontChannelLogoutService frontChannelLogoutService;
  private final LogoutNotificationRepository logoutNotificationRepository;

  public LogoutOrchestrator(
      OIDCSessionManager sessionManager,
      BackChannelLogoutService backChannelLogoutService,
      FrontChannelLogoutService frontChannelLogoutService,
      LogoutNotificationRepository logoutNotificationRepository) {
    this.sessionManager = sessionManager;
    this.backChannelLogoutService = backChannelLogoutService;
    this.frontChannelLogoutService = frontChannelLogoutService;
    this.logoutNotificationRepository = logoutNotificationRepository;
  }

  /**
   * Executes RP-Initiated Logout with context object.
   *
   * @param tenant the tenant
   * @param context the logout context containing session and signing information
   * @param resolver the resolver for client logout URIs
   * @return the logout result
   */
  public LogoutResult executeRPInitiatedLogout(
      Tenant tenant, LogoutContext context, ClientLogoutUriResolver resolver) {
    return executeRPInitiatedLogout(
        tenant,
        context.opSessionId(),
        context.sub(),
        context.initiatorClientId(),
        context.issuer(),
        context.signingKeyJwks(),
        context.signingAlgorithm(),
        resolver);
  }

  /**
   * Executes RP-Initiated Logout.
   *
   * @deprecated Use {@link #executeRPInitiatedLogout(Tenant, LogoutContext,
   *     ClientLogoutUriResolver)} instead
   */
  @Deprecated
  public LogoutResult executeRPInitiatedLogout(
      Tenant tenant,
      OPSessionIdentifier opSessionId,
      String sub,
      String initiatorClientId,
      String issuer,
      String signingKeyJwks,
      String signingAlgorithm,
      ClientLogoutUriResolver resolver) {

    // Terminate OP session and get all client sessions
    ClientSessions clientSessions =
        sessionManager.terminateOPSession(tenant, opSessionId, TerminationReason.USER_LOGOUT);

    if (clientSessions.isEmpty()) {
      log.info("No client sessions found for opSessionId: {}", opSessionId.value());
      return LogoutResult.empty();
    }

    // Send Back-Channel notifications
    List<LogoutNotification> backChannelNotifications =
        sendBackChannelNotifications(
            tenant,
            opSessionId,
            issuer,
            clientSessions,
            signingKeyJwks,
            signingAlgorithm,
            resolver);

    // Build Front-Channel iframes
    List<FrontChannelLogoutIframe> frontChannelIframes =
        frontChannelLogoutService.buildLogoutIframes(issuer, clientSessions, resolver);

    log.info(
        "Logout completed. opSessionId: {}, clientSessions: {}, backChannel: {}, frontChannel: {}",
        opSessionId.value(),
        clientSessions.size(),
        backChannelNotifications.size(),
        frontChannelIframes.size());

    return new LogoutResult(backChannelNotifications, frontChannelIframes);
  }

  private List<LogoutNotification> sendBackChannelNotifications(
      Tenant tenant,
      OPSessionIdentifier opSessionId,
      String issuer,
      ClientSessions clientSessions,
      String signingKeyJwks,
      String signingAlgorithm,
      ClientLogoutUriResolver resolver) {

    List<LogoutNotification> notifications = new ArrayList<>();

    for (ClientSession session : clientSessions) {
      String backchannelLogoutUri = resolver.resolveBackChannelLogoutUri(session.clientId());
      if (backchannelLogoutUri == null || backchannelLogoutUri.isEmpty()) {
        continue;
      }

      // Generate logout token
      LogoutToken logoutToken =
          backChannelLogoutService.generateLogoutToken(
              issuer, session.clientId(), session.sub(), session.sid());

      // Create notification record (initially PENDING)
      LogoutNotification notification =
          LogoutNotification.createBackChannel(
              opSessionId, session.clientId(), session.sid(), logoutToken.jti());
      logoutNotificationRepository.save(tenant, notification);

      try {
        // Encode and send notification
        String encodedToken =
            backChannelLogoutService.encodeLogoutToken(
                logoutToken, signingAlgorithm, signingKeyJwks);

        BackChannelNotificationResult result =
            backChannelLogoutService.sendNotification(
                backchannelLogoutUri, encodedToken, DEFAULT_BACKCHANNEL_TIMEOUT_MS);

        // Update notification with result
        if (result.isSuccess()) {
          notification.markSuccess(result.httpStatusCode());
          backChannelLogoutService.markJtiUsed(logoutToken.jti(), 120);
        } else if (result.isTimeout()) {
          notification.markTimeout(result.errorMessage());
        } else {
          notification.markFailed(result.httpStatusCode(), result.errorMessage());
        }
      } catch (Exception e) {
        log.error("Failed to send back-channel notification to {}", backchannelLogoutUri, e);
        notification.markFailed(0, e.getMessage());
      }

      logoutNotificationRepository.update(tenant, notification);
      notifications.add(notification);
    }

    return notifications;
  }

  public static class LogoutResult {
    private final List<LogoutNotification> backChannelNotifications;
    private final List<FrontChannelLogoutIframe> frontChannelIframes;

    public LogoutResult(
        List<LogoutNotification> backChannelNotifications,
        List<FrontChannelLogoutIframe> frontChannelIframes) {
      this.backChannelNotifications = backChannelNotifications;
      this.frontChannelIframes = frontChannelIframes;
    }

    public static LogoutResult empty() {
      return new LogoutResult(List.of(), List.of());
    }

    public List<LogoutNotification> backChannelNotifications() {
      return backChannelNotifications;
    }

    public List<FrontChannelLogoutIframe> frontChannelIframes() {
      return frontChannelIframes;
    }

    public boolean hasFrontChannelIframes() {
      return !frontChannelIframes.isEmpty();
    }

    public String buildFrontChannelHtml() {
      StringBuilder html = new StringBuilder();
      html.append("<!DOCTYPE html><html><head><title>Logout</title></head><body>");
      for (FrontChannelLogoutIframe iframe : frontChannelIframes) {
        html.append(iframe.toHtml());
      }
      html.append("<script>setTimeout(function(){window.location.reload();},5000);</script>");
      html.append("</body></html>");
      return html.toString();
    }
  }
}
