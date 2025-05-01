package org.idp.server.core.security.event;

import org.idp.server.core.identity.verification.IdentityVerificationProcess;
import org.idp.server.core.identity.verification.IdentityVerificationType;
import org.idp.server.core.security.SecurityEvent;
import org.idp.server.core.security.SecurityEventPublisher;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.token.OAuthToken;
import org.idp.server.basic.type.security.RequestAttributes;

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
      IdentityVerificationType type,
      IdentityVerificationProcess identityVerificationProcess,
      boolean result,
      RequestAttributes requestAttributes) {
    String resultString = result ? "success" : "failure";
    SecurityEventType securityEventType =
        new SecurityEventType(
            type.name() + "_" + identityVerificationProcess.name() + "_" + resultString);
    SecurityEventDescription securityEventDescription =
        new SecurityEventDescription(securityEventType.value());
    TokenEventCreator eventCreator =
        new TokenEventCreator(
            tenant, oAuthToken, securityEventType, securityEventDescription, requestAttributes);
    SecurityEvent securityEvent = eventCreator.create();
    securityEventPublisher.publish(securityEvent);
  }
}
