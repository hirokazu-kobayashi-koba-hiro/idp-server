package org.idp.sample;

import java.util.List;
import java.util.Map;
import org.idp.server.IdpServerApplication;
import org.idp.server.OAuthApi;
import org.idp.server.basic.date.SystemDateTime;
import org.idp.server.handler.oauth.io.*;
import org.idp.server.oauth.authentication.Authentication;
import org.idp.server.oauth.identity.User;
import org.idp.server.type.extension.OAuthDenyReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("")
public class OAuthController implements ParameterTransformable {

  Logger log = LoggerFactory.getLogger(OAuthController.class);

  OAuthApi oAuthApi;
  UserMockService userMockService;

  public OAuthController(
      IdpServerApplication idpServerApplication, UserMockService userMockService) {
    this.oAuthApi = idpServerApplication.oAuthApi();
    oAuthApi.setOAuthRequestDelegate(userMockService);
    this.userMockService = userMockService;
  }

  @GetMapping("{tenant-id}/v1/authorizations")
  public String get(
      @RequestParam(required = false) MultiValueMap<String, String> request,
      @PathVariable("tenant-id") String tenantId,
      Model model) {
    Map<String, String[]> params = transform(request);
    Tenant tenant = Tenant.of(tenantId);
    OAuthRequest oAuthRequest = new OAuthRequest(params, tenant.issuer());
    OAuthRequestResponse response = oAuthApi.request(oAuthRequest);
    switch (response.status()) {
      case OK -> {
        model.addAttribute("id", response.authorizationRequestId());
        model.addAttribute("tenantId", tenant.id());
        model.addAttribute("clientName", response.clientConfiguration().clientName());
        model.addAttribute("scopes", response.scopeList());
        return "authorizations";
      }
      case NO_INTERACTION_OK, REDIRECABLE_BAD_REQUEST -> {
        return "redirect:" + response.redirectUri();
      }
      default -> {
        model.addAttribute("error", response.error());
        model.addAttribute("errorDescription", response.errorDescription());
        return "error";
      }
    }
  }

  @PostMapping("/v1/authorize")
  public String authorize(
      @ModelAttribute("username") String username,
      @ModelAttribute("password") String password,
      @ModelAttribute("id") String id,
      @ModelAttribute("tenantId") String tenantId) {
    Tenant tenant = Tenant.of(tenantId);
    User user = userMockService.getUser();
    Authentication authentication =
        new Authentication()
            .setTime(SystemDateTime.now())
            .setMethods(List.of("password"))
            .setAcrValues(List.of("urn:mace:incommon:iap:silver"));

    OAuthAuthorizeRequest authAuthorizeRequest =
        new OAuthAuthorizeRequest(id, tenant.issuer(), user).setAuthentication(authentication);
    OAuthAuthorizeResponse authAuthorizeResponse = oAuthApi.authorize(authAuthorizeRequest);
    return "redirect:" + authAuthorizeResponse.redirectUriValue();
  }

  @PostMapping("/v1/deny")
  public String deny(@ModelAttribute("id") String id, @ModelAttribute("tenantId") String tenantId) {
    Tenant tenant = Tenant.of(tenantId);
    OAuthDenyRequest denyRequest =
        new OAuthDenyRequest(id, tenant.issuer(), OAuthDenyReason.access_denied);
    OAuthDenyResponse oAuthDenyResponse = oAuthApi.deny(denyRequest);
    return "redirect:" + oAuthDenyResponse.redirectUriValue();
  }
}
