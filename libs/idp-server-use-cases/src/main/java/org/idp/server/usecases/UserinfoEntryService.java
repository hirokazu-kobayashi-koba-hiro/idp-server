package org.idp.server.usecases;

import org.idp.server.basic.datasource.Transaction;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.UserRepository;
import org.idp.server.core.security.event.DefaultSecurityEventType;
import org.idp.server.core.security.event.TokenEventPublisher;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.tenant.TenantRepository;
import org.idp.server.basic.type.oauth.Subject;
import org.idp.server.basic.type.security.RequestAttributes;
import org.idp.server.core.userinfo.UserinfoApi;
import org.idp.server.core.userinfo.UserinfoProtocol;
import org.idp.server.core.userinfo.UserinfoProtocols;
import org.idp.server.core.userinfo.handler.UserinfoDelegate;
import org.idp.server.core.userinfo.handler.io.UserinfoRequest;
import org.idp.server.core.userinfo.handler.io.UserinfoRequestResponse;

@Transaction
public class UserinfoEntryService implements UserinfoApi, UserinfoDelegate {

  UserinfoProtocols userinfoProtocols;
  UserRepository userRepository;
  TenantRepository tenantRepository;
  TokenEventPublisher eventPublisher;

  public UserinfoEntryService(
      UserinfoProtocols userinfoProtocols,
      UserRepository userRepository,
      TenantRepository tenantRepository,
      TokenEventPublisher eventPublisher) {
    this.userinfoProtocols = userinfoProtocols;
    this.userRepository = userRepository;
    this.tenantRepository = tenantRepository;
    this.eventPublisher = eventPublisher;
  }

  @Override
  public User findUser(Tenant tenant, Subject subject) {
    return userRepository.get(tenant, subject.value());
  }

  public UserinfoRequestResponse request(
      TenantIdentifier tenantIdentifier,
      String authorizationHeader,
      String clientCert,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantRepository.get(tenantIdentifier);
    UserinfoRequest userinfoRequest = new UserinfoRequest(tenant, authorizationHeader);
    userinfoRequest.setClientCert(clientCert);

    UserinfoProtocol userinfoProtocol =
        userinfoProtocols.get(tenant.authorizationProtocolProvider());

    UserinfoRequestResponse result = userinfoProtocol.request(userinfoRequest, this);

    if (result.isOK()) {
      eventPublisher.publish(
          tenant,
          result.oAuthToken(),
          DefaultSecurityEventType.userinfo_success,
          requestAttributes);
    }

    return result;
  }
}
