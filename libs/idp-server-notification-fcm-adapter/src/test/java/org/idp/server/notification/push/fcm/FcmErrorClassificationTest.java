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

import static org.junit.jupiter.api.Assertions.*;

import com.google.firebase.messaging.MessagingErrorCode;
import org.junit.jupiter.api.Test;

class FcmErrorClassificationTest {

  @Test
  void errorLevelForConfigurationIssues() {
    assertTrue(FcmErrorClassification.of(MessagingErrorCode.INVALID_ARGUMENT).isError());
    assertTrue(FcmErrorClassification.of(MessagingErrorCode.SENDER_ID_MISMATCH).isError());
    assertTrue(FcmErrorClassification.of(MessagingErrorCode.THIRD_PARTY_AUTH_ERROR).isError());
  }

  @Test
  void warnLevelForTransientOrLifecycleIssues() {
    assertFalse(FcmErrorClassification.of(MessagingErrorCode.UNREGISTERED).isError());
    assertFalse(FcmErrorClassification.of(MessagingErrorCode.QUOTA_EXCEEDED).isError());
    assertFalse(FcmErrorClassification.of(MessagingErrorCode.UNAVAILABLE).isError());
    assertFalse(FcmErrorClassification.of(MessagingErrorCode.INTERNAL).isError());
  }

  @Test
  void unknownErrorCodeFallsBackToError() {
    FcmErrorClassification classification = FcmErrorClassification.of(null);
    assertNotNull(classification);
    assertTrue(classification.isError());
  }
}
