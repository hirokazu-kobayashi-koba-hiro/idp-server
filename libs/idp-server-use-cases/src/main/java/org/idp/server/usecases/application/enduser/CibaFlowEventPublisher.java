package org.idp.server.usecases.application.enduser;

import org.idp.server.core.extension.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.core.identity.User;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.SecurityEvent;
import org.idp.server.platform.security.SecurityEventPublisher;
import org.idp.server.platform.security.event.DefaultSecurityEventType;
import org.idp.server.platform.security.type.RequestAttributes;

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
