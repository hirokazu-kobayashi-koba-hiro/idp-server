package org.idp.server.core;

import org.idp.server.core.basic.sql.Transactional;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.oauth.identity.UserRepository;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.tenant.TenantRepository;
import org.idp.server.core.type.oauth.Subject;
import org.idp.server.core.userinfo.UserinfoApi;
import org.idp.server.core.userinfo.UserinfoProtocol;
import org.idp.server.core.userinfo.UserinfoProtocols;
import org.idp.server.core.userinfo.handler.UserinfoDelegate;
import org.idp.server.core.userinfo.handler.io.UserinfoRequest;
import org.idp.server.core.userinfo.handler.io.UserinfoRequestResponse;

@Transactional
public class UserinfoEntryService implements UserinfoApi, UserinfoDelegate {

  UserinfoProtocols userinfoProtocols;
  UserRepository userRepository;
  TenantRepository tenantRepository;

  public UserinfoEntryService(
      UserinfoProtocols userinfoProtocols,
      UserRepository userRepository,
      TenantRepository tenantRepository) {
    this.userinfoProtocols = userinfoProtocols;
    this.userRepository = userRepository;
    this.tenantRepository = tenantRepository;
  }

  @Override
  public User findUser(Tenant tenant, Subject subject) {
    return userRepository.get(tenant, subject.value());
  }

  public UserinfoRequestResponse request(
      TenantIdentifier tenantIdentifier, String authorizationHeader, String clientCert) {

    Tenant tenant = tenantRepository.get(tenantIdentifier);
    UserinfoRequest userinfoRequest = new UserinfoRequest(tenant, authorizationHeader);
    userinfoRequest.setClientCert(clientCert);

    UserinfoProtocol userinfoProtocol =
        userinfoProtocols.get(tenant.authorizationProtocolProvider());

    return userinfoProtocol.request(userinfoRequest, this);
  }
}
