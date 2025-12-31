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

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import org.idp.server.core.openid.session.ClientSessionIdentifier;
import org.idp.server.core.openid.session.OPSessionIdentifier;

public class LogoutNotification implements Serializable {

  private Long id;
  private OPSessionIdentifier opSessionId;
  private String clientId;
  private ClientSessionIdentifier sid;
  private LogoutNotificationType notificationType;
  private String logoutTokenJti;
  private LogoutNotificationStatus status;
  private Integer httpStatusCode;
  private String errorMessage;
  private Instant attemptedAt;
  private Instant completedAt;

  public LogoutNotification() {}

  public LogoutNotification(
      Long id,
      OPSessionIdentifier opSessionId,
      String clientId,
      ClientSessionIdentifier sid,
      LogoutNotificationType notificationType,
      String logoutTokenJti,
      LogoutNotificationStatus status,
      Integer httpStatusCode,
      String errorMessage,
      Instant attemptedAt,
      Instant completedAt) {
    this.id = id;
    this.opSessionId = opSessionId;
    this.clientId = clientId;
    this.sid = sid;
    this.notificationType = notificationType;
    this.logoutTokenJti = logoutTokenJti;
    this.status = status;
    this.httpStatusCode = httpStatusCode;
    this.errorMessage = errorMessage;
    this.attemptedAt = attemptedAt;
    this.completedAt = completedAt;
  }

  public static LogoutNotification createBackChannel(
      OPSessionIdentifier opSessionId,
      String clientId,
      ClientSessionIdentifier sid,
      String logoutTokenJti) {
    return new LogoutNotification(
        null,
        opSessionId,
        clientId,
        sid,
        LogoutNotificationType.BACK_CHANNEL,
        logoutTokenJti,
        LogoutNotificationStatus.PENDING,
        null,
        null,
        Instant.now(),
        null);
  }

  public static LogoutNotification createFrontChannel(
      OPSessionIdentifier opSessionId, String clientId, ClientSessionIdentifier sid) {
    return new LogoutNotification(
        null,
        opSessionId,
        clientId,
        sid,
        LogoutNotificationType.FRONT_CHANNEL,
        null,
        LogoutNotificationStatus.PENDING,
        null,
        null,
        Instant.now(),
        null);
  }

  public Long id() {
    return id;
  }

  public OPSessionIdentifier opSessionId() {
    return opSessionId;
  }

  public String clientId() {
    return clientId;
  }

  public ClientSessionIdentifier sid() {
    return sid;
  }

  public LogoutNotificationType notificationType() {
    return notificationType;
  }

  public String logoutTokenJti() {
    return logoutTokenJti;
  }

  public LogoutNotificationStatus status() {
    return status;
  }

  public Integer httpStatusCode() {
    return httpStatusCode;
  }

  public String errorMessage() {
    return errorMessage;
  }

  public Instant attemptedAt() {
    return attemptedAt;
  }

  public Instant completedAt() {
    return completedAt;
  }

  public boolean isBackChannel() {
    return notificationType == LogoutNotificationType.BACK_CHANNEL;
  }

  public boolean isFrontChannel() {
    return notificationType == LogoutNotificationType.FRONT_CHANNEL;
  }

  public LogoutNotification markSuccess(int httpStatusCode) {
    this.status = LogoutNotificationStatus.SUCCESS;
    this.httpStatusCode = httpStatusCode;
    this.completedAt = Instant.now();
    return this;
  }

  public LogoutNotification markFailed(int httpStatusCode, String errorMessage) {
    this.status = LogoutNotificationStatus.FAILED;
    this.httpStatusCode = httpStatusCode;
    this.errorMessage = errorMessage;
    this.completedAt = Instant.now();
    return this;
  }

  public LogoutNotification markTimeout(String errorMessage) {
    this.status = LogoutNotificationStatus.TIMEOUT;
    this.errorMessage = errorMessage;
    this.completedAt = Instant.now();
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    LogoutNotification that = (LogoutNotification) o;
    return Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
