package org.idp.server.security.event.hook.ssf;

import java.util.Map;
import org.idp.server.platform.security.SecurityEvent;

public class SecurityEventTokenEntityConvertor {

  SecurityEvent securityEvent;

  public SecurityEventTokenEntityConvertor(SecurityEvent securityEvent) {
    this.securityEvent = securityEvent;
  }

  public SecurityEventTokenEntity convert() {
    String tokenIssuer = securityEvent.tokenIssuer();
    String requestedClientId = securityEvent.clientId();
    SharedSecurityEvent sharedSecurityEvent = convertToSecurityEvent();

    return new SecurityEventTokenEntity(tokenIssuer, requestedClientId, sharedSecurityEvent);
  }

  private SharedSecurityEvent convertToSecurityEvent() {
    SecurityEventType securityEventType = SecurityEventType.of(securityEvent.type());

    Map<String, String> subjectMap =
        Map.of("sub", securityEvent.userSub(), "iss", securityEvent.tokenIssuerValue());
    SecurityEventSubjectPayload securityEventSubjectPayload =
        new SecurityEventSubjectPayload(subjectMap);
    SecurityEventSubject subject =
        new SecurityEventSubject(SecuritySubjectFormat.iss_sub, securityEventSubjectPayload);

    Map<String, Object> payload = securityEvent.toMap();
    SecurityEventPayload eventPayload = new SecurityEventPayload(payload);

    return new SharedSecurityEvent(securityEventType, subject, eventPayload);
  }
}
