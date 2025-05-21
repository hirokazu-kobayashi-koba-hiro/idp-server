package org.idp.server.core.extension.ciba;

import org.idp.server.core.extension.ciba.handler.io.*;
import org.idp.server.core.extension.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.core.extension.ciba.request.BackchannelAuthenticationRequestIdentifier;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.dependency.protocol.AuthorizationProvider;

public interface CibaProtocol {

  AuthorizationProvider authorizationProtocolProvider();

  CibaRequestResult request(CibaRequest request);

  CibaIssueResponse issueResponse(CibaIssueRequest cibaIssueRequest);

  BackchannelAuthenticationRequest get(
      Tenant tenant,
      BackchannelAuthenticationRequestIdentifier backchannelAuthenticationRequestIdentifier);

  CibaAuthorizeResponse authorize(CibaAuthorizeRequest request);

  CibaDenyResponse deny(CibaDenyRequest request);
}
