package org.idp.server.ciba;

public class CibaRequestAnalyzer {

  public CibaRequestPattern analyze(CibaRequestParameters parameters) {
    if (parameters.hasRequest()) {
      return CibaRequestPattern.REQUEST_OBJECT;
    }
    return CibaRequestPattern.NORMAL;
  }
}
