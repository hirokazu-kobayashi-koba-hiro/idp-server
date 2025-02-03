package org.idp.server.api;

import org.idp.server.handler.userinfo.UserinfoDelegate;
import org.idp.server.handler.userinfo.io.UserinfoRequest;
import org.idp.server.handler.userinfo.io.UserinfoRequestResponse;

public interface UserinfoApi {

  UserinfoRequestResponse request(UserinfoRequest request, UserinfoDelegate delegate);
}
