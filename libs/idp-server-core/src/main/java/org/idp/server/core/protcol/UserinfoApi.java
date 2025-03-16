package org.idp.server.core.protcol;

import org.idp.server.core.handler.userinfo.UserinfoDelegate;
import org.idp.server.core.handler.userinfo.io.UserinfoRequest;
import org.idp.server.core.handler.userinfo.io.UserinfoRequestResponse;

public interface UserinfoApi {

  UserinfoRequestResponse request(UserinfoRequest request, UserinfoDelegate delegate);
}
