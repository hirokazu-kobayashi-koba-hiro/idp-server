package org.idp.sample.presentation.view;

import jakarta.servlet.http.HttpSession;
import org.idp.sample.application.service.OAuthFlowService;
import org.idp.sample.domain.model.tenant.TenantIdentifier;
import org.idp.sample.presentation.api.ParameterTransformable;
import org.idp.server.handler.oauth.io.OAuthLogoutResponse;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.util.Map;

@RestController
@RequestMapping("/{tenant-id}/v1/logout")
public class OidcLogoutV1Api implements ParameterTransformable {

  OAuthFlowService oAuthFlowService;
  HttpSession httpSession;

  public OidcLogoutV1Api(OAuthFlowService oAuthFlowService, HttpSession httpSession) {
    this.oAuthFlowService = oAuthFlowService;
    this.httpSession = httpSession;
  }

  @GetMapping
  public Object logout(@RequestParam(required = false) MultiValueMap<String, String> request,
                       @PathVariable("tenant-id") TenantIdentifier tenantId) {
    String id = httpSession.getId();
    System.out.println(id);
    Map<String, String[]> params = transform(request);
    OAuthLogoutResponse response = oAuthFlowService.logout(tenantId, params);
    return new RedirectView(response.redirectUriValue());
  }
}
