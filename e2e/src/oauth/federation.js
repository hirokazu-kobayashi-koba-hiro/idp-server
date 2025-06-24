import {
  authorize,
  deny,
} from "../api/oauthClient";
import { backendUrl, serverConfig } from "../tests/testConfig";
import { convertNextAction, convertToAuthorizationResponse } from "../lib/util";
import { get, post } from "../lib/http";

export const requestFederation = async ({
  url,
  authSessionTenantId,
  authSessionId,
  federationTenantId,
  type,
  providerName,
  action = "authorize",
  user,
  interaction = (id, user) => {
    console.log(id, user);
  },
}) => {

    const federationResponse = await post({
      url: `${url}/${authSessionTenantId}/v1/authorizations/${authSessionId}/federations/${type}/${providerName}`
    });

    console.log(federationResponse.data.redirect_uri);

    const response = await get({
      url: federationResponse.data.redirect_uri
    });

    // console.log(response.headers);
    console.log(response.data);
    const { location } = response.headers;
    const { nextAction, params } = convertNextAction(location);

    if (nextAction !== "goAuthentication") {
      console.debug("redirect");

      const authorizationResponse = convertToAuthorizationResponse(location);
      return {
        status: response.status,
        authorizationResponse,
        error: {
          error: authorizationResponse.error,
          error_description: authorizationResponse.errorDescription,
        }
      };
    }

    if (response.status !== 302) {
      return {
        status: response.status,
        error: response.data,
      };
    }

    const id = params.get("id");
    console.log(id);

    if (action === "authorize") {


      await interaction(id, user);

      const authorizeResponse = await authorize({
        endpoint: `${backendUrl}/${federationTenantId}/v1/authorizations/{id}/authorize`,
        id,
        body: {
          action: "signin"
        }
      });

      // console.log(authorizeResponse.headers);
      console.log(authorizeResponse.data);
      console.log(authorizeResponse.status);
      const redirectUri = authorizeResponse.data.redirect_uri;
      const query = redirectUri.includes("?")
        ? redirectUri.split("?")[1]
        : redirectUri.split("#")[1];
      const params = new URLSearchParams(query);

      return {
        status: authorizeResponse.status,
        params,
      };
    } else {
      const denyResponse = await deny({
        endpoint: serverConfig.denyEndpoint,
        id,
      });
      console.log(denyResponse.data);
      const authorizationResponse = convertToAuthorizationResponse(
        denyResponse.data.redirect_uri
      );
      return {
        status: denyResponse.status,
        authorizationResponse,
      };
    }

};
