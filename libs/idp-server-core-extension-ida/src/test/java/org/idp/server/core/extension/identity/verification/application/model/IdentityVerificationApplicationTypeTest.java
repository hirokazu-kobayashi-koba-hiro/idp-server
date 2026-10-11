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

import static org.junit.jupiter.api.Assertions.*;

import org.idp.server.core.extension.identity.verification.IdentityVerificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** An application is processed only under the type it was filed under (Issue #1969). */
class IdentityVerificationApplicationTypeTest {

  private IdentityVerificationApplication filedUnder(IdentityVerificationType type) {
    return new IdentityVerificationApplication(
        null, type, null, null, null, null, null, null, null, null, null);
  }

  @Test
  @DisplayName("申込みの種別と同じなら true")
  void matchesSameType() {
    assertTrue(
        filedUnder(new IdentityVerificationType("kyc"))
            .isOfType(new IdentityVerificationType("kyc")));
  }

  @Test
  @DisplayName("申込みの種別と違えば false")
  void rejectsOtherType() {
    assertFalse(
        filedUnder(new IdentityVerificationType("kyc"))
            .isOfType(new IdentityVerificationType("ongoing")));
  }

  @Test
  @DisplayName("申込みに種別が無ければ、どの種別とも一致しない")
  void applicationWithoutTypeMatchesNothing() {
    assertFalse(filedUnder(null).isOfType(new IdentityVerificationType("kyc")));
    assertFalse(
        new IdentityVerificationApplication().isOfType(new IdentityVerificationType("kyc")));
  }
}
