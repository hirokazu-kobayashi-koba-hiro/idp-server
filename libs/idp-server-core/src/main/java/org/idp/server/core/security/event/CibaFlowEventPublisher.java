package org.idp.server.core.security.event;

import org.idp.server.core.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.core.identity.User;
import org.idp.server.core.security.SecurityEvent;
import org.idp.server.core.security.SecurityEventPublisher;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.basic.type.security.RequestAttributes;

public class CibaFlowEventPublisher {

  SecurityEventPublisher securityEventPublisher;

  public CibaFlowEventPublisher(SecurityEventPublisher securityEventPublisher) {
    this.securityEventPublisher = securityEventPublisher;
  }

  public void publish(
      Tenant tenant,
      BackchannelAuthenticationRequest request,
      User user,
      DefaultSecurityEventType type,
      RequestAttributes requestAttributes) {
    CibaFlowEventCreator eventCreator =
        new CibaFlowEventCreator(tenant, request, user, type, requestAttributes);
    SecurityEvent securityEvent = eventCreator.create();
    securityEventPublisher.publish(securityEvent);
  }
}
