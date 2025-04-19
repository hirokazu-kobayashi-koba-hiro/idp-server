package org.idp.server.core;

import java.util.Map;
import org.idp.server.core.basic.datasource.Transaction;
import org.idp.server.core.ciba.*;
import org.idp.server.core.ciba.handler.io.*;
import org.idp.server.core.oauth.identity.UserRepository;
import org.idp.server.core.security.event.CibaFlowEventPublisher;
import org.idp.server.core.security.event.DefaultSecurityEventType;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.tenant.TenantRepository;
import org.idp.server.core.type.security.RequestAttributes;

@Transaction
public class CibaFlowEntryService implements CibaFlowApi {

  CibaProtocols cibaProtocols;
  UserRepository userRepository;
  TenantRepository tenantRepository;
  CibaFlowEventPublisher eventPublisher;

  public CibaFlowEntryService(
      CibaProtocols cibaProtocols,
      UserRepository userRepository,
      TenantRepository tenantRepository,
      CibaFlowEventPublisher eventPublisher) {
    this.cibaProtocols = cibaProtocols;
    this.userRepository = userRepository;
    this.tenantRepository = tenantRepository;
    this.eventPublisher = eventPublisher;
  }

  public CibaRequestResponse request(
      TenantIdentifier tenantIdentifier,
      Map<String, String[]> params,
      String authorizationHeader,
      String clientCert,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantRepository.get(tenantIdentifier);
    CibaRequest cibaRequest = new CibaRequest(tenant, authorizationHeader, params);
    cibaRequest.setClientCert(clientCert);

    CibaProtocol cibaProtocol = cibaProtocols.get(tenant.authorizationProtocolProvider());

    CibaRequestResponse requestResponse = cibaProtocol.request(cibaRequest);

    // TODO remove if statement
    if (requestResponse.isOK()) {
      eventPublisher.publish(
          tenant,
          requestResponse.request(),
          requestResponse.user(),
          DefaultSecurityEventType.backchannel_authentication_request_success,
          requestAttributes);
    }

    return requestResponse;
  }

  public CibaAuthorizeResponse authorize(
      TenantIdentifier tenantIdentifier, String authReqId, RequestAttributes requestAttributes) {

    Tenant tenant = tenantRepository.get(tenantIdentifier);
    CibaAuthorizeRequest cibaAuthorizeRequest = new CibaAuthorizeRequest(tenant, authReqId);

    CibaProtocol cibaProtocol = cibaProtocols.get(tenant.authorizationProtocolProvider());

    return cibaProtocol.authorize(cibaAuthorizeRequest);
  }

  public CibaDenyResponse deny(
      TenantIdentifier tenantIdentifier, String authReqId, RequestAttributes requestAttributes) {

    Tenant tenant = tenantRepository.get(tenantIdentifier);
    CibaDenyRequest cibaDenyRequest = new CibaDenyRequest(tenant, authReqId);

    CibaProtocol cibaProtocol = cibaProtocols.get(tenant.authorizationProtocolProvider());

    return cibaProtocol.deny(cibaDenyRequest);
  }
}
