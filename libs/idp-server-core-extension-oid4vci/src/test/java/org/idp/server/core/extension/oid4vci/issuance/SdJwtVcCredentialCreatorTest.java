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
package org.idp.server.core.extension.oid4vci.issuance;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SdJwtVcCredentialCreatorTest {

  @Test
  @DisplayName("iat は UTC の日の始まりに切り下げる（RFC 9901 Section 10.1、時刻で VC を結び付けさせない）")
  void issuanceTimeIsRoundedDownToTheStartOfTheDay() {
    long startOfDay = 1_790_294_400L; // 2026-09-25T00:00:00Z

    assertEquals(startOfDay, SdJwtVcCredentialCreator.startOfDay(startOfDay));
    assertEquals(startOfDay, SdJwtVcCredentialCreator.startOfDay(startOfDay + 1));
    assertEquals(startOfDay, SdJwtVcCredentialCreator.startOfDay(startOfDay + 86_399));
    assertEquals(startOfDay + 86_400, SdJwtVcCredentialCreator.startOfDay(startOfDay + 86_400));
  }
}
