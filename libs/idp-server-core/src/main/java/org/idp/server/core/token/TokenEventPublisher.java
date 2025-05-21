package org.idp.server.core.token;

import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.SecurityEvent;
import org.idp.server.platform.security.SecurityEventPublisher;
import org.idp.server.platform.security.event.DefaultSecurityEventType;
import org.idp.server.platform.security.event.SecurityEventDescription;
import org.idp.server.platform.security.event.SecurityEventType;
import org.idp.server.platform.security.type.RequestAttributes;

public class TokenEventPublisher {

  SecurityEventPublisher securityEventPublisher;

  public TokenEventPublisher(SecurityEventPublisher securityEventPublisher) {
    this.securityEventPublisher = securityEventPublisher;
  }

  public void publish(
      Tenant tenant,
      OAuthToken oAuthToken,
      DefaultSecurityEventType type,
      RequestAttributes requestAttributes) {
    TokenEventCreator eventCreator =
        new TokenEventCreator(tenant, oAuthToken, type, requestAttributes);
    SecurityEvent securityEvent = eventCreator.create();
    securityEventPublisher.publish(securityEvent);
  }

  public void publish(
      Tenant tenant,
      OAuthToken oAuthToken,
      SecurityEventType securityEventType,
      RequestAttributes requestAttributes) {
    SecurityEventDescription securityEventDescription =
        new SecurityEventDescription(securityEventType.value());
    TokenEventCreator eventCreator =
        new TokenEventCreator(
            tenant, oAuthToken, securityEventType, securityEventDescription, requestAttributes);
    SecurityEvent securityEvent = eventCreator.create();
    securityEventPublisher.publish(securityEvent);
  }
}
