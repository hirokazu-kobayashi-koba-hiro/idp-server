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

package org.idp.server.notification.push.fcm;

import com.google.firebase.messaging.MessagingErrorCode;

/**
 * FCM v1 API error code classification for log level determination.
 *
 * @see <a href="https://firebase.google.com/docs/cloud-messaging/error-codes">FCM error codes</a>
 */
enum FcmErrorClassification {
  INVALID_ARGUMENT(MessagingErrorCode.INVALID_ARGUMENT, LogLevel.ERROR),
  SENDER_ID_MISMATCH(MessagingErrorCode.SENDER_ID_MISMATCH, LogLevel.ERROR),
  THIRD_PARTY_AUTH_ERROR(MessagingErrorCode.THIRD_PARTY_AUTH_ERROR, LogLevel.ERROR),
  UNREGISTERED(MessagingErrorCode.UNREGISTERED, LogLevel.WARN),
  QUOTA_EXCEEDED(MessagingErrorCode.QUOTA_EXCEEDED, LogLevel.WARN),
  UNAVAILABLE(MessagingErrorCode.UNAVAILABLE, LogLevel.WARN),
  INTERNAL(MessagingErrorCode.INTERNAL, LogLevel.WARN),
  UNKNOWN(null, LogLevel.ERROR);

  enum LogLevel {
    ERROR,
    WARN
  }

  private final MessagingErrorCode messagingErrorCode;
  private final LogLevel logLevel;

  FcmErrorClassification(MessagingErrorCode messagingErrorCode, LogLevel logLevel) {
    this.messagingErrorCode = messagingErrorCode;
    this.logLevel = logLevel;
  }

  static FcmErrorClassification of(MessagingErrorCode messagingErrorCode) {
    for (FcmErrorClassification classification : values()) {
      if (classification.messagingErrorCode == messagingErrorCode) {
        return classification;
      }
    }
    return UNKNOWN;
  }

  boolean isError() {
    return logLevel == LogLevel.ERROR;
  }
}
