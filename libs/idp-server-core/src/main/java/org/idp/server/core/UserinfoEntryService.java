package org.idp.server.core;

import org.idp.server.core.api.UserinfoApi;
import org.idp.server.core.basic.sql.Transactional;
import org.idp.server.core.handler.userinfo.UserinfoDelegate;
import org.idp.server.core.handler.userinfo.io.UserinfoRequest;
import org.idp.server.core.handler.userinfo.io.UserinfoRequestResponse;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.protocol.UserinfoProtocol;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.tenant.TenantRepository;
import org.idp.server.core.type.oauth.Subject;
import org.idp.server.core.user.UserRepository;

@Transactional
public class UserinfoEntryService implements UserinfoApi, UserinfoDelegate {

  UserinfoProtocol userinfoProtocol;
  UserRepository userRepository;
  TenantRepository tenantRepository;

  public UserinfoEntryService(
      UserinfoProtocol userinfoProtocol,
      UserRepository userRepository,
      TenantRepository tenantRepository) {
    this.userinfoProtocol = userinfoProtocol;
    this.userRepository = userRepository;
    this.tenantRepository = tenantRepository;
  }

  @Override
  public User findUser(Tenant tenant, Subject subject) {
    return userRepository.get(subject.value());
  }

  public UserinfoRequestResponse request(
      TenantIdentifier tenantId, String authorizationHeader, String clientCert) {

    Tenant tenant = tenantRepository.get(tenantId);
    UserinfoRequest userinfoRequest = new UserinfoRequest(tenant, authorizationHeader);
    userinfoRequest.setClientCert(clientCert);

    return userinfoProtocol.request(userinfoRequest, this);
  }
}
