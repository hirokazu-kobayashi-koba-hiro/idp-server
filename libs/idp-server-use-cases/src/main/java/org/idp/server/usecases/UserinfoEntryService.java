package org.idp.server.usecases;

import org.idp.server.basic.datasource.Transaction;
import org.idp.server.basic.type.oauth.Subject;
import org.idp.server.basic.type.security.RequestAttributes;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.UserIdentifier;
import org.idp.server.core.identity.repository.UserQueryRepository;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.core.multi_tenancy.tenant.TenantRepository;
import org.idp.server.core.oidc.userinfo.UserinfoApi;
import org.idp.server.core.oidc.userinfo.UserinfoProtocol;
import org.idp.server.core.oidc.userinfo.UserinfoProtocols;
import org.idp.server.core.oidc.userinfo.handler.UserinfoDelegate;
import org.idp.server.core.oidc.userinfo.handler.io.UserinfoRequest;
import org.idp.server.core.oidc.userinfo.handler.io.UserinfoRequestResponse;
import org.idp.server.core.security.event.DefaultSecurityEventType;
import org.idp.server.core.security.event.TokenEventPublisher;

@Transaction
public class UserinfoEntryService implements UserinfoApi, UserinfoDelegate {

  UserinfoProtocols userinfoProtocols;
  UserQueryRepository userQueryRepository;
  TenantRepository tenantRepository;
  TokenEventPublisher eventPublisher;

  public UserinfoEntryService(
      UserinfoProtocols userinfoProtocols,
      UserQueryRepository userQueryRepository,
      TenantRepository tenantRepository,
      TokenEventPublisher eventPublisher) {
    this.userinfoProtocols = userinfoProtocols;
    this.userQueryRepository = userQueryRepository;
    this.tenantRepository = tenantRepository;
    this.eventPublisher = eventPublisher;
  }

  @Override
  public User findUser(Tenant tenant, Subject subject) {
    UserIdentifier userIdentifier = new UserIdentifier(subject.value());
    return userQueryRepository.get(tenant, userIdentifier);
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
