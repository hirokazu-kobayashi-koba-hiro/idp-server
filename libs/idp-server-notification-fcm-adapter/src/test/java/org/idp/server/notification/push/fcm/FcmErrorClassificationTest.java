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
