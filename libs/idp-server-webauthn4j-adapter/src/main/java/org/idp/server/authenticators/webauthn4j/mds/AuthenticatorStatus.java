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

package org.idp.server.authenticators.webauthn4j.mds;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.idp.server.platform.json.JsonReadable;

public class AuthenticatorStatus implements JsonReadable {

  String aaguid;
  boolean found;
  boolean compromised;
  boolean fidoCertified;
  String latestStatus;
  LocalDate effectiveDate;
  List<String> statusHistory;

  public AuthenticatorStatus() {
    this.statusHistory = new ArrayList<>();
  }

  private AuthenticatorStatus(
      String aaguid,
      boolean found,
      boolean compromised,
      boolean fidoCertified,
      String latestStatus,
      LocalDate effectiveDate,
      List<String> statusHistory) {
    this.aaguid = aaguid;
    this.found = found;
    this.compromised = compromised;
    this.fidoCertified = fidoCertified;
    this.latestStatus = latestStatus;
    this.effectiveDate = effectiveDate;
    this.statusHistory = statusHistory;
  }

  public static AuthenticatorStatus notFound(String aaguid) {
    return new AuthenticatorStatus(aaguid, false, false, false, "NOT_FOUND", null, List.of());
  }

  public static AuthenticatorStatus of(
      String aaguid,
      com.webauthn4j.metadata.data.toc.AuthenticatorStatus status,
      LocalDate effectiveDate,
      List<String> statusHistory) {

    boolean compromised = isCompromisedStatus(status);
    boolean fidoCertified = isFidoCertified(status);

    return new AuthenticatorStatus(
        aaguid,
        true,
        compromised,
        fidoCertified,
        status != null ? status.name() : "UNKNOWN",
        effectiveDate,
        statusHistory);
  }

  private static boolean isCompromisedStatus(
      com.webauthn4j.metadata.data.toc.AuthenticatorStatus status) {
    if (status == null) {
      return false;
    }
    return switch (status) {
      case ATTESTATION_KEY_COMPROMISE,
              USER_VERIFICATION_BYPASS,
              USER_KEY_REMOTE_COMPROMISE,
              USER_KEY_PHYSICAL_COMPROMISE,
              REVOKED ->
          true;
      default -> false;
    };
  }

  private static boolean isFidoCertified(
      com.webauthn4j.metadata.data.toc.AuthenticatorStatus status) {
    if (status == null) {
      return false;
    }
    return switch (status) {
      case FIDO_CERTIFIED, FIDO_CERTIFIED_L1, FIDO_CERTIFIED_L2, FIDO_CERTIFIED_L3 -> true;
      default -> false;
    };
  }

  public String aaguid() {
    return aaguid;
  }

  public boolean isFound() {
    return found;
  }

  public boolean isCompromised() {
    return compromised;
  }

  public boolean isFidoCertified() {
    return fidoCertified;
  }

  public String latestStatus() {
    return latestStatus;
  }

  public LocalDate effectiveDate() {
    return effectiveDate;
  }

  public List<String> statusHistory() {
    return statusHistory;
  }

  public boolean isTrusted() {
    return found && !compromised;
  }
}
