package org.idp.server.oauth.authentication;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Authentication {
  LocalDateTime authenticationTime;
  List<String> methods = new ArrayList<>();

  public Authentication() {}

  public List<String> methods() {
    return methods;
  }

  public boolean hasMethod() {
    return !methods.isEmpty();
  }
}
