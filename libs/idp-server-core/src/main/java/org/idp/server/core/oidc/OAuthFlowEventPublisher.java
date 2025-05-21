package org.idp.server.core.oidc;

import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.request.AuthorizationRequest;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.SecurityEvent;
import org.idp.server.platform.security.SecurityEventPublisher;
import org.idp.server.platform.security.event.DefaultSecurityEventType;
import org.idp.server.platform.security.type.RequestAttributes;

public class OAuthFlowEventPublisher {

  SecurityEventPublisher securityEventPublisher;

  public OAuthFlowEventPublisher(SecurityEventPublisher securityEventPublisher) {
    this.securityEventPublisher = securityEventPublisher;
  }

  public void publish(
      Tenant tenant,
      AuthorizationRequest authorizationRequest,
      User user,
      DefaultSecurityEventType type,
      RequestAttributes requestAttributes) {
    OAuthFlowEventCreator eventCreator =
        new OAuthFlowEventCreator(tenant, authorizationRequest, user, type, requestAttributes);
    SecurityEvent securityEvent = eventCreator.create();
    securityEventPublisher.publish(securityEvent);
  }
}
