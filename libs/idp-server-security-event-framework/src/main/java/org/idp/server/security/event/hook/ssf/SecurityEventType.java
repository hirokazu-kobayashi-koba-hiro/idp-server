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

import org.idp.server.platform.security.event.DefaultSecurityEventType;

/**
 * @see <a href="https://openid.net/specs/openid-risc-profile-specification-1_0.html">RISC</a>
 * @see <a href="https://openid.net/specs/openid-caep-specification-1_0.html">CAEP</a>
 */
public enum SecurityEventType {
  // RISC
  AccountCredentialChangeRequired(
      "RISC",
      DefaultSecurityEventType.password_change.name(),
      "https://schemas.openid.net/secevent/risc/event-type/account-credential-change-required"),
  AccountPurged(
      "RISC",
      DefaultSecurityEventType.user_deletion.name(),
      "https://schemas.openid.net/secevent/risc/event-type/account-purged"),
  AccountDisabled(
      "RISC",
      DefaultSecurityEventType.user_disabled.name(),
      "https://schemas.openid.net/secevent/risc/event-type/account-disabled"),
  AccountEnabled("RISC", "", "https://schemas.openid.net/secevent/risc/event-type/account-enabled"),
  IdentifierChanged(
      "RISC", "", "https://schemas.openid.net/secevent/risc/event-type/identifier-changed"),
  IdentifierRecycled(
      "RISC", "", "https://schemas.openid.net/secevent/risc/event-type/identifier-recycled"),
  CredentialCompromise(
      "RISC", "", "https://schemas.openid.net/secevent/risc/event-type/credential-compromise"),
  OptIn("RISC", "", "https://schemas.openid.net/secevent/risc/event-type/opt-in"),
  OptOutInitiated(
      "RISC", "", "https://schemas.openid.net/secevent/risc/event-type/opt-out-initiated"),
  OptOutCanceled(
      "RISC", "", "https://schemas.openid.net/secevent/risc/event-type/opt-out-cancelled"),
  OptOutEffective(
      "RISC", "", "https://schemas.openid.net/secevent/risc/event-type/opt-out-effective"),
  RecoveryActivated(
      "RISC", "", "https://schemas.openid.net/secevent/risc/event-type/recovery-activated"),
  RecoveryInformationChanged(
      "RISC",
      "",
      "https://schemas.openid.net/secevent/risc/event-type/recovery-information-changed"),
  SessionsRevoked(
      "RISC", "", "https://schemas.openid.net/secevent/risc/event-type/sessions-revoked"),
  // CAEP
  SessionRevoked(
      "CAEP",
      DefaultSecurityEventType.logout.name(),
      "https://schemas.openid.net/secevent/caep/event-type/session-revoked"),
  TokenClaimsChange(
      "CAEP", "", "https://schemas.openid.net/secevent/caep/event-type/token-claims-change"),
  CredentialChange(
      "CAEP",
      DefaultSecurityEventType.password_change.name(),
      "https://schemas.openid.net/secevent/caep/event-type/credential-change"),
  AssuranceLevelChange(
      "CAEP", "", "https://schemas.openid.net/secevent/caep/event-type/assurance-level-change"),
  DeviceComplianceChange(
      "CAEP", "", "https://schemas.openid.net/secevent/caep/event-type/device-compliance-change"),
  Undefined("", "", "");

  String type;
  String name;
  String typeUri;

  SecurityEventType(String type, String name, String typeUri) {
    this.type = type;
    this.name = name;
    this.typeUri = typeUri;
  }

  public static SecurityEventType of(
      org.idp.server.platform.security.event.SecurityEventType type) {

    for (SecurityEventType securityEventType : values()) {
      if (securityEventType.name.equals(type.value())) {
        return securityEventType;
      }
    }

    return SecurityEventType.Undefined;
  }

  public String type() {
    return type;
  }

  public SecurityEventTypeIdentifier typeIdentifier() {
    return new SecurityEventTypeIdentifier(typeUri);
  }

  public boolean isRISC() {
    return type.equals("RISC");
  }

  public boolean isDefined() {
    return this != Undefined;
  }
}
