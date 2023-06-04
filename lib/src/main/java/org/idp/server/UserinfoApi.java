package org.idp.server;

import org.idp.server.handler.userinfo.UserinfoDelegate;
import org.idp.server.handler.userinfo.UserinfoHandler;
import org.idp.server.handler.userinfo.io.UserinfoRequest;
import org.idp.server.handler.userinfo.io.UserinfoRequestResponse;
import org.idp.server.handler.userinfo.io.UserinfoRequestStatus;
import org.idp.server.type.oauth.Error;
import org.idp.server.type.oauth.ErrorDescription;
import org.idp.server.userinfo.UserinfoErrorResponse;

import java.util.logging.Level;
import java.util.logging.Logger;

public class UserinfoApi {

  UserinfoHandler userinfoHandler;
  Logger log = Logger.getLogger(UserinfoApi.class.getName());

  UserinfoApi(UserinfoHandler userinfoHandler) {
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
