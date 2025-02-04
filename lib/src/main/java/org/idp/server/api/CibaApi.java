package org.idp.server.api;

import org.idp.server.ciba.CibaRequestDelegate;
import org.idp.server.handler.ciba.io.*;

public interface CibaApi {

  CibaRequestResponse request(CibaRequest request, CibaRequestDelegate delegate);

  CibaAuthorizeResponse authorize(CibaAuthorizeRequest request);

  CibaDenyResponse deny(CibaDenyRequest request);
}
