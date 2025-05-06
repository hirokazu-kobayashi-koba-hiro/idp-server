package org.idp.server.core.security.event;

import java.util.HashMap;
import org.idp.server.basic.type.security.RequestAttributes;
import org.idp.server.core.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.core.identity.User;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.security.SecurityEvent;

public class CibaFlowEventCreator {

  Tenant tenant;
  BackchannelAuthenticationRequest request;
  User user;
  SecurityEventType securityEventType;
  SecurityEventDescription securityEventDescription;
  RequestAttributes requestAttributes;

  public CibaFlowEventCreator(Tenant tenant, BackchannelAuthenticationRequest request, User user, DefaultSecurityEventType defaultSecurityEventType, RequestAttributes requestAttributes) {
    this.tenant = tenant;
    this.request = request;
    this.user = user;
    this.securityEventType = defaultSecurityEventType.toEventType();
    this.securityEventDescription = defaultSecurityEventType.toEventDescription();
    this.requestAttributes = requestAttributes;
  }

  public SecurityEvent create() {
    HashMap<String, Object> detailsMap = new HashMap<>();
    SecurityEventBuilder builder = new SecurityEventBuilder();
    builder.add(securityEventType);
    builder.add(securityEventDescription);

    SecurityEventTenant securityEventTenant = new SecurityEventTenant(tenant.identifier().value(), tenant.tokenIssuer().value(), tenant.name().value());
    builder.add(securityEventTenant);

    SecurityEventClient securityEventClient = new SecurityEventClient(request.requestedClientId().value(), "");
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
