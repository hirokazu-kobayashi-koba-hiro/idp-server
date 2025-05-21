package org.idp.server.authentication.interactors.sms.external;

import org.idp.server.basic.json.JsonReadable;

public class ExternalSmsAuthenticationTransaction implements JsonReadable {

  String id;

  public ExternalSmsAuthenticationTransaction() {}

  public ExternalSmsAuthenticationTransaction(String id) {
    this.id = id;
  }

  public String id() {
    return id;
  }
}
