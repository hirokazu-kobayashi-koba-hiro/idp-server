package org.idp.server.core.security.event;

import java.util.HashMap;
import org.idp.server.core.identity.User;
import org.idp.server.core.oidc.request.AuthorizationRequest;
import org.idp.server.core.security.SecurityEvent;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.basic.type.security.RequestAttributes;

public class OAuthFlowEventCreator {

  Tenant tenant;
  AuthorizationRequest authorizationRequest;
  User user;
  SecurityEventType securityEventType;
  SecurityEventDescription securityEventDescription;
  RequestAttributes requestAttributes;

  public OAuthFlowEventCreator(
      Tenant tenant,
      AuthorizationRequest authorizationRequest,
      User user,
      DefaultSecurityEventType defaultSecurityEventType,
      RequestAttributes requestAttributes) {
    this.tenant = tenant;
    this.authorizationRequest = authorizationRequest;
    this.user = user;
    this.securityEventType = defaultSecurityEventType.toEventType();
    this.securityEventDescription = defaultSecurityEventType.toEventDescription();
    this.requestAttributes = requestAttributes;
  }

  public OAuthFlowEventCreator(
      AuthorizationRequest authorizationRequest,
      User user,
      SecurityEventType securityEventType,
      SecurityEventDescription securityEventDescription,
      RequestAttributes requestAttributes) {
    this.authorizationRequest = authorizationRequest;
    this.user = user;
    this.securityEventType = securityEventType;
    this.securityEventDescription = securityEventDescription;
    this.requestAttributes = requestAttributes;
  }

  public SecurityEvent create() {
    HashMap<String, Object> detailsMap = new HashMap<>();
    SecurityEventBuilder builder = new SecurityEventBuilder();
    builder.add(securityEventType);
    builder.add(securityEventDescription);

    SecurityEventTenant securityEventTenant =
        new SecurityEventTenant(
            tenant.identifier().value(), tenant.tokenIssuer().value(), tenant.name().value());
    builder.add(securityEventTenant);

    SecurityEventClient securityEventClient =
        new SecurityEventClient(
            authorizationRequest.retrieveClientId().value(),
            authorizationRequest.clientNameValue());
    builder.add(securityEventClient);

    if (user != null) {
      SecurityEventUser securityEventUser = new SecurityEventUser(user.sub(), user.name());
      builder.add(securityEventUser);
      detailsMap.put("user", user.toMap());
    }

    builder.add(requestAttributes.getIpAddress());
    builder.add(requestAttributes.getUserAgent());
    detailsMap.putAll(requestAttributes.toMap());

    SecurityEventDetail securityEventDetail = new SecurityEventDetail(detailsMap);

    builder.add(securityEventDetail);

    return builder.build();
  }
}
