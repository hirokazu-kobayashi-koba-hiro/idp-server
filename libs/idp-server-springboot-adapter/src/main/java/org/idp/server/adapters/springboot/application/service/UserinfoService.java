package org.idp.server.adapters.springboot.application.service;

import org.idp.server.adapters.springboot.application.service.tenant.TenantService;
import org.idp.server.core.UserManagementApi;
import org.idp.server.core.adapters.IdpServerApplication;
import org.idp.server.core.UserinfoApi;
import org.idp.server.core.handler.userinfo.UserinfoDelegate;
import org.idp.server.core.handler.userinfo.io.UserinfoRequest;
import org.idp.server.core.handler.userinfo.io.UserinfoRequestResponse;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.type.oauth.Subject;
import org.idp.server.core.type.oauth.TokenIssuer;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.tenant.TenantIdentifier;
import org.springframework.stereotype.Service;

@Service
public class UserinfoService implements UserinfoDelegate {

  UserinfoApi userinfoApi;
  UserManagementApi userManagementApi;
  TenantService tenantService;

  public UserinfoService(
      IdpServerApplication idpServerApplication,
      TenantService tenantService) {
    this.userinfoApi = idpServerApplication.userinfoApi();
    this.userManagementApi = idpServerApplication.userManagementApi();
    this.tenantService = tenantService;
  }

  @Override
  public User findUser(TokenIssuer tokenIssuer, Subject subject) {
    return userManagementApi.get(subject.value());
  }

  public UserinfoRequestResponse request(
      TenantIdentifier tenantId, String authorizationHeader, String clientCert) {

    Tenant tenant = tenantService.get(tenantId);
    UserinfoRequest userinfoRequest = new UserinfoRequest(authorizationHeader, tenant.issuer());
    userinfoRequest.setClientCert(clientCert);

    return userinfoApi.request(userinfoRequest, this);
  }
}
