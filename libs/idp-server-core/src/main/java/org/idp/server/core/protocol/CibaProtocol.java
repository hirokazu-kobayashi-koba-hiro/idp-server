package org.idp.server.core.protocol;

import org.idp.server.core.ciba.CibaRequestDelegate;
import org.idp.server.core.handler.ciba.io.*;

public interface CibaProtocol {

  CibaRequestResponse request(CibaRequest request, CibaRequestDelegate delegate);

  CibaAuthorizeResponse authorize(CibaAuthorizeRequest request);

  CibaDenyResponse deny(CibaDenyRequest request);
}
