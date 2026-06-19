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

package org.idp.server.core.extension.identity.verification.application.model;

import java.util.Arrays;
import java.util.List;

public enum IdentityVerificationApplicationStatus {
  REQUESTED("requested"),
  APPLYING("applying"),
  APPLIED("applied"),
  EXAMINATION_PROCESSING("examination_processing"),
  APPROVED("approved"),
  REJECTED("rejected"),
  EXPIRED("expired"),
  CANCELLED("cancelled"),
  UNDEFINED(""),
  UNKNOWN("unknown");

  String value;

  IdentityVerificationApplicationStatus(String value) {
    this.value = value;
  }

  public static IdentityVerificationApplicationStatus of(String value) {
    for (IdentityVerificationApplicationStatus status :
        IdentityVerificationApplicationStatus.values()) {
      if (status.value.equals(value)) {
        return status;
      }
    }
    return UNKNOWN;
  }

  public String value() {
    return value;
  }

  public boolean isRunning() {
    return this == REQUESTED
        || this == APPLYING
        || this == APPLIED
        || this == EXAMINATION_PROCESSING;
  }

  /**
   * Terminal (absorbing) states. Once an application reaches a terminal state, subsequent process /
   * callback evaluations must not move it.
   */
  public boolean isTerminal() {
    return this == APPROVED || this == REJECTED || this == EXPIRED || this == CANCELLED;
  }

  /**
   * Forward progress order within the running phases (REQUESTED → APPLYING → APPLIED →
   * EXAMINATION_PROCESSING). Used to forbid backward transitions. Returns -1 for non-running
   * states.
   */
  public int runningRank() {
    return switch (this) {
      case REQUESTED -> 0;
      case APPLYING -> 1;
      case APPLIED -> 2;
      case EXAMINATION_PROCESSING -> 3;
      default -> -1;
    };
  }

  public static List<String> runningValues() {
    return Arrays.stream(values())
        .filter(IdentityVerificationApplicationStatus::isRunning)
        .map(IdentityVerificationApplicationStatus::value)
        .toList();
  }

  public boolean isApproved() {
    return this == APPROVED;
  }

  public boolean isRejected() {
    return this == REJECTED;
  }

  public boolean isCancelled() {
    return this == CANCELLED;
  }
}
