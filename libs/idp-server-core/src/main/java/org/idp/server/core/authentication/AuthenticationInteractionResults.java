package org.idp.server.core.authentication;

import java.util.Iterator;
import java.util.List;

public class AuthenticationInteractionResults implements Iterable<AuthenticationInteractionResult> {

  List<AuthenticationInteractionResult> values;

  @Override
  public Iterator<AuthenticationInteractionResult> iterator() {
    return values.iterator();
  }
}
