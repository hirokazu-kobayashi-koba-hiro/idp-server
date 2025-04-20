package org.idp.server.core.ciba;

import org.idp.server.core.basic.dependency.protcol.AuthorizationProtocolProvider;
import org.idp.server.core.ciba.handler.io.*;
import org.idp.server.core.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.type.ciba.AuthReqId;

public interface CibaProtocol {

  AuthorizationProtocolProvider authorizationProtocolProvider();

  CibaRequestResponse request(CibaRequest request);

  BackchannelAuthenticationRequest get(Tenant tenant, AuthReqId authReqId);

  CibaAuthorizeResponse authorize(CibaAuthorizeRequest request);

  CibaDenyResponse deny(CibaDenyRequest request);
}
