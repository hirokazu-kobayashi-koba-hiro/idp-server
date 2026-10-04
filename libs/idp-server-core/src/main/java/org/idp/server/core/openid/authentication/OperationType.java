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

package org.idp.server.core.openid.authentication;

public enum OperationType {
  CHALLENGE,
  AUTHENTICATION,
  REGISTRATION,
  DENY,
  DE_REGISTRATION,
  NO_ACTION,
  /**
   * A check of something the end-user knows about themselves — a birthdate, the last digits of a
   * phone number — against what the account holds (Issue #1907).
   *
   * <p>Deliberately not {@link #AUTHENTICATION}: such values can be known to others, so passing
   * this proves neither who is at the keyboard nor possession of anything. It is not counted as an
   * authentication factor, is not reported in {@code amr}, and earns no browser proof.
   */
  VERIFICATION,
  UNKNOWN;

  public static OperationType of(String type) {
    for (OperationType operationType : OperationType.values()) {
      if (operationType.name().equalsIgnoreCase(type)) {
        return operationType;
      }
    }
    return UNKNOWN;
  }

  public boolean isAuthentication() {
    return this == AUTHENTICATION;
  }

  public boolean isDeny() {
    return this == DENY;
  }

  public boolean isChallenge() {
    return this == CHALLENGE;
  }

  public boolean isVerification() {
    return this == VERIFICATION;
  }

  /**
   * Whether the end-user had to supply something only they hold.
   *
   * <p>Separates the steps that say something about who is at the keyboard from the ones that do
   * not. Sending a code, cancelling, or acknowledging a notification can be done by anyone who
   * knows the request id; entering a password or a one-time code cannot. A {@link #VERIFICATION}
   * does not count either: the values it checks can be known to others, so passing it says nothing
   * about who is at the keyboard.
   */
  public boolean provesPossession() {
    return this == AUTHENTICATION || this == REGISTRATION;
  }
}
