package org.idp.server.core;

import java.util.Map;
import org.idp.server.core.basic.sql.Transactional;
import org.idp.server.core.ciba.CibaFlowApi;
import org.idp.server.core.ciba.CibaProtocol;
import org.idp.server.core.ciba.CibaRequestDelegate;
import org.idp.server.core.ciba.UserCriteria;
import org.idp.server.core.ciba.handler.io.*;
import org.idp.server.core.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.oauth.identity.UserRepository;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.tenant.TenantRepository;
import org.idp.server.core.type.ciba.UserCode;

@Transactional
public class CibaFlowEntryService implements CibaFlowApi, CibaRequestDelegate {

  CibaProtocol cibaProtocol;
  UserRepository userRepository;
  TenantRepository tenantRepository;

  public CibaFlowEntryService(
      CibaProtocol cibaProtocol, UserRepository userRepository, TenantRepository tenantRepository) {
    this.cibaProtocol = cibaProtocol;
    this.userRepository = userRepository;
    this.tenantRepository = tenantRepository;
  }

  public CibaRequestResponse request(
      TenantIdentifier tenantIdentifier,
      Map<String, String[]> params,
      String authorizationHeader,
      String clientCert) {

    Tenant tenant = tenantRepository.get(tenantIdentifier);
    CibaRequest cibaRequest = new CibaRequest(tenant, authorizationHeader, params);
    cibaRequest.setClientCert(clientCert);

    return cibaProtocol.request(cibaRequest, this);
  }

  public CibaAuthorizeResponse authorize(TenantIdentifier tenantIdentifier, String authReqId) {

    Tenant tenant = tenantRepository.get(tenantIdentifier);
    CibaAuthorizeRequest cibaAuthorizeRequest = new CibaAuthorizeRequest(tenant, authReqId);

    return cibaProtocol.authorize(cibaAuthorizeRequest);
  }

  public CibaDenyResponse deny(TenantIdentifier tenantIdentifier, String authReqId) {

    Tenant tenant = tenantRepository.get(tenantIdentifier);
    CibaDenyRequest cibaDenyRequest = new CibaDenyRequest(tenant, authReqId);

    return cibaProtocol.deny(cibaDenyRequest);
  }

  @Override
  public User find(TenantIdentifier tenantIdentifier, UserCriteria criteria) {
    Tenant tenant = tenantRepository.get(tenantIdentifier);
    if (tenant.exists() && criteria.hasLoginHint()) {
      // TODO proverId
      return userRepository.findBy(tenant, criteria.loginHint().value(), "idp-server");
    }
    return User.notFound();
  }

  // TODO implement
  @Override
  public boolean authenticate(TenantIdentifier tenantIdentifier, User user, UserCode userCode) {
    return true;
  }

  @Override
  public void notify(
      TenantIdentifier tenantIdentifier, User user, BackchannelAuthenticationRequest request) {}
}
