package org.idp.server.adapters.springboot.application.service;

import java.util.Map;
import org.idp.server.adapters.springboot.application.service.tenant.TenantService;
import org.idp.server.core.UserManagementApi;
import org.idp.server.core.adapters.IdpServerApplication;
import org.idp.server.core.CibaApi;
import org.idp.server.core.ciba.CibaRequestDelegate;
import org.idp.server.core.ciba.UserCriteria;
import org.idp.server.core.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.core.handler.ciba.io.*;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.type.ciba.UserCode;
import org.idp.server.core.type.oauth.TokenIssuer;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.tenant.TenantIdentifier;
import org.springframework.stereotype.Service;

@Service
public class CibaFlowService implements CibaRequestDelegate {

  CibaApi cibaApi;
  UserManagementApi userManagementApi;
  TenantService tenantService;

  public CibaFlowService(
      IdpServerApplication idpServerApplication,
      TenantService tenantService) {
    this.cibaApi = idpServerApplication.cibaApi();
    this.userManagementApi = idpServerApplication.userManagementApi();
    this.tenantService = tenantService;
  }

  public CibaRequestResponse request(
      TenantIdentifier tenantIdentifier,
      Map<String, String[]> params,
      String authorizationHeader,
      String clientCert) {

    Tenant tenant = tenantService.get(tenantIdentifier);
    CibaRequest cibaRequest = new CibaRequest(authorizationHeader, params, tenant.issuer());
    cibaRequest.setClientCert(clientCert);

    return cibaApi.request(cibaRequest, this);
  }

  public CibaAuthorizeResponse authorize(TenantIdentifier tenantIdentifier, String authReqId) {

    Tenant tenant = tenantService.get(tenantIdentifier);
    CibaAuthorizeRequest cibaAuthorizeRequest =
        new CibaAuthorizeRequest(authReqId, tenant.issuer());

    return cibaApi.authorize(cibaAuthorizeRequest);
  }

  public CibaDenyResponse deny(TenantIdentifier tenantIdentifier, String authReqId) {

    Tenant tenant = tenantService.get(tenantIdentifier);
    CibaDenyRequest cibaDenyRequest = new CibaDenyRequest(authReqId, tenant.issuer());

    return cibaApi.deny(cibaDenyRequest);
  }

  @Override
  public User find(TokenIssuer tokenIssuer, UserCriteria criteria) {
    Tenant tenant = tenantService.find(tokenIssuer);
    if (tenant.exists() && criteria.hasLoginHint()) {
      // TODO proverId
      return userManagementApi.findBy(tenant, criteria.loginHint().value(), "idp-server");
    }
    return User.notFound();
  }

  @Override
  public boolean authenticate(TokenIssuer tokenIssuer, User user, UserCode userCode) {
    return userManagementApi.authenticate(user, userCode.value());
  }

  @Override
  public void notify(
      TokenIssuer tokenIssuer, User user, BackchannelAuthenticationRequest request) {}
}
