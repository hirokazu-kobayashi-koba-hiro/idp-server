package org.idp.server.core;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.idp.server.core.api.UserinfoApi;
import org.idp.server.core.basic.sql.Transactional;
import org.idp.server.core.handler.userinfo.UserinfoDelegate;
import org.idp.server.core.handler.userinfo.UserinfoHandler;
import org.idp.server.core.handler.userinfo.io.UserinfoRequest;
import org.idp.server.core.handler.userinfo.io.UserinfoRequestResponse;
import org.idp.server.core.handler.userinfo.io.UserinfoRequestStatus;
import org.idp.server.core.type.oauth.Error;
import org.idp.server.core.type.oauth.ErrorDescription;
import org.idp.server.core.userinfo.UserinfoErrorResponse;

@Transactional
public class UserinfoApiImpl implements UserinfoApi {

  UserinfoHandler userinfoHandler;
  Logger log = Logger.getLogger(UserinfoApi.class.getName());

  UserinfoApiImpl(UserinfoHandler userinfoHandler) {
    this.userinfoHandler = userinfoHandler;
  }

  public UserinfoRequestResponse request(UserinfoRequest request, UserinfoDelegate delegate) {
    try {
      return userinfoHandler.handle(request, delegate);
    } catch (Exception exception) {
      Error error = new Error("server_error");
      ErrorDescription errorDescription = new ErrorDescription(exception.getMessage());
      log.log(Level.SEVERE, exception.getMessage(), exception);
      return new UserinfoRequestResponse(
          UserinfoRequestStatus.SERVER_ERROR, new UserinfoErrorResponse(error, errorDescription));
    }
  }
}
