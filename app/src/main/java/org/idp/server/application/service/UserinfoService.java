package org.idp.server.application.service;

import org.idp.server.application.service.tenant.TenantService;
import org.idp.server.application.service.user.internal.UserService;
import org.idp.server.core.IdpServerApplication;
import org.idp.server.core.api.UserinfoApi;
import org.idp.server.core.handler.userinfo.UserinfoDelegate;
import org.idp.server.core.handler.userinfo.io.UserinfoRequest;
import org.idp.server.core.handler.userinfo.io.UserinfoRequestResponse;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.type.oauth.Subject;
import org.idp.server.core.type.oauth.TokenIssuer;
import org.idp.server.domain.model.tenant.Tenant;
import org.idp.server.domain.model.tenant.TenantIdentifier;
import org.springframework.stereotype.Service;

@Service
public class UserinfoService implements UserinfoDelegate {

  UserinfoApi userinfoApi;
  UserService userService;
  TenantService tenantService;

  public UserinfoService(
      IdpServerApplication idpServerApplication,
      UserService userService,
      TenantService tenantService) {
    this.userinfoApi = idpServerApplication.userinfoApi();
    this.userService = userService;
    this.tenantService = tenantService;
  }

  @Override
  public User findUser(TokenIssuer tokenIssuer, Subject subject) {
    return userService.get(subject.value());
  }

  public UserinfoRequestResponse request(
      TenantIdentifier tenantId, String authorizationHeader, String clientCert) {

    Tenant tenant = tenantService.get(tenantId);
    UserinfoRequest userinfoRequest = new UserinfoRequest(authorizationHeader, tenant.issuer());
    userinfoRequest.setClientCert(clientCert);

    return userinfoApi.request(userinfoRequest, this);
  }
}
