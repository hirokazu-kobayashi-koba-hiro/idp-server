package org.idp.server.core.sharedsignal;

import java.util.Map;
import org.idp.server.core.type.oauth.RequestedClientId;
import org.idp.server.core.type.oauth.TokenIssuer;

public class SecurityEventTokenEntityConvertor {

  Event event;

  public SecurityEventTokenEntityConvertor(Event event) {
    this.event = event;
  }

  public SecurityEventTokenEntity convert() {
    TokenIssuer tokenIssuer = event.tokenIssuer();
    RequestedClientId requestedClientId = event.clientId();
    SecurityEvent securityEvent = convertToSecurityEvent();

    return new SecurityEventTokenEntity(tokenIssuer, requestedClientId, securityEvent);
  }

  private SecurityEvent convertToSecurityEvent() {
    SecurityEventType securityEventType = SecurityEventType.of(event.type());

    Map<String, String> subjectMap =
        Map.of("sub", event.userSub(), "iss", event.tokenIssuerValue());
    SecurityEventSubjectPayload securityEventSubjectPayload =
        new SecurityEventSubjectPayload(subjectMap);
    SecurityEventSubject subject =
        new SecurityEventSubject(SecuritySubjectFormat.iss_sub, securityEventSubjectPayload);

    Map<String, Object> payload = event.toMap();
    SecurityEventPayload eventPayload = new SecurityEventPayload(payload);

    return new SecurityEvent(securityEventType, subject, eventPayload);
  }
}
