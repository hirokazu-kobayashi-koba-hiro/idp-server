package org.idp.server.handler.userinfo.io;

import org.idp.server.userinfo.UserinfoErrorResponse;
import org.idp.server.userinfo.UserinfoResponse;

public class UserinfoRequestResponse {
  UserinfoRequestStatus status;
  UserinfoResponse response;
  UserinfoErrorResponse errorResponse;
}
