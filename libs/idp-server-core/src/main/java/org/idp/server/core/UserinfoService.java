package org.idp.server.core;

import org.idp.server.core.basic.sql.Transactional;
import org.idp.server.core.function.UserinfoFunction;
import org.idp.server.core.handler.userinfo.UserinfoDelegate;
import org.idp.server.core.handler.userinfo.io.UserinfoRequest;
import org.idp.server.core.handler.userinfo.io.UserinfoRequestResponse;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.protcol.UserinfoApi;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.tenant.TenantService;
import org.idp.server.core.type.oauth.Subject;
import org.idp.server.core.type.oauth.TokenIssuer;
import org.idp.server.core.user.UserService;

@Transactional
public class UserinfoService implements UserinfoFunction, UserinfoDelegate {

  UserinfoApi userinfoApi;
  UserService userService;
  TenantService tenantService;

  public UserinfoService(
      UserinfoApi userinfoApi, UserService userService, TenantService tenantService) {
    this.userinfoApi = userinfoApi;
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
