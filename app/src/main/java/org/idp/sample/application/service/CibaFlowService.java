package org.idp.sample.application.service;

import java.util.Map;
import org.idp.sample.application.service.tenant.TenantService;
import org.idp.sample.application.service.user.UserService;
import org.idp.sample.domain.model.tenant.Tenant;
import org.idp.sample.domain.model.tenant.TenantIdentifier;
import org.idp.server.IdpServerApplication;
import org.idp.server.api.CibaApi;
import org.idp.server.ciba.CibaRequestDelegate;
import org.idp.server.ciba.UserCriteria;
import org.idp.server.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.handler.ciba.io.*;
import org.idp.server.oauth.identity.User;
import org.idp.server.type.ciba.UserCode;
import org.idp.server.type.oauth.TokenIssuer;
import org.springframework.stereotype.Service;

@Service
public class CibaFlowService implements CibaRequestDelegate {

  CibaApi cibaApi;
  UserService userService;
  TenantService tenantService;

  public CibaFlowService(
      IdpServerApplication idpServerApplication,
      UserService userService,
      TenantService tenantService) {
    this.cibaApi = idpServerApplication.cibaApi();
    this.userService = userService;
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
    if (criteria.hasLoginHint()) {
      return userService.find(criteria.loginHint().value());
    }
    return User.notFound();
  }

  @Override
  public boolean authenticate(TokenIssuer tokenIssuer, User user, UserCode userCode) {
    return userService.authenticate(user, userCode.value());
  }

  @Override
  public void notify(
      TokenIssuer tokenIssuer, User user, BackchannelAuthenticationRequest request) {}
}
