package org.idp.server.core.adapters.security.hook;

import org.idp.server.core.basic.json.JsonReadable;

public class SlackSecurityEventHookConfiguration implements JsonReadable {

  String url;
  String messageTemplate;

  public String url() {
    return url;
  }

  public String messageTemplate() {
    return messageTemplate;
  }
}
