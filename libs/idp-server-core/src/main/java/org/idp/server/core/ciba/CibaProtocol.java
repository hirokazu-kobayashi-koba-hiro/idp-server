package org.idp.server.core.ciba;

import org.idp.server.core.ciba.handler.io.*;

public interface CibaProtocol {

  CibaRequestResponse request(CibaRequest request, CibaRequestDelegate delegate);

  CibaAuthorizeResponse authorize(CibaAuthorizeRequest request);

  CibaDenyResponse deny(CibaDenyRequest request);
}
