package org.idp.server.core.ciba;

import org.idp.server.basic.dependency.protocol.AuthorizationProtocolProvider;
import org.idp.server.core.ciba.handler.io.*;
import org.idp.server.core.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.core.ciba.request.BackchannelAuthenticationRequestIdentifier;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

public interface CibaProtocol {

  AuthorizationProtocolProvider authorizationProtocolProvider();

  CibaRequestResult request(CibaRequest request);

  CibaIssueResponse issueResponse(CibaIssueRequest cibaIssueRequest);

  BackchannelAuthenticationRequest get(
      Tenant tenant,
      BackchannelAuthenticationRequestIdentifier backchannelAuthenticationRequestIdentifier);

  CibaAuthorizeResponse authorize(CibaAuthorizeRequest request);

  CibaDenyResponse deny(CibaDenyRequest request);
}
