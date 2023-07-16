package org.idp.server.type.verifiablecredential;

import java.util.ArrayList;
import java.util.List;

public class VerifiableCredentialContext {
  public static List<String> values = new ArrayList<>();

  static {
    values.add("https://www.w3.org/2018/credentials/v1");
    values.add("https://www.w3.org/2018/credentials/examples/v1");
  }
}
