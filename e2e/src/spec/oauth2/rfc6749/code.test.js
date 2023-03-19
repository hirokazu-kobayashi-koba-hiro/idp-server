import { describe, expect } from "@jest/globals"
import { get, post } from "../../../lib/http"

describe("The OAuth 2.0 Authorization Framework", () => {
  describe("3.1.  Authorization Endpoint", () => {
    it("The authorization endpoint is used to interact with the resource owner and obtain an authorization grant.  The authorization server MUST first verify the identity of the resource owner.", async () => {})

    it('The authorization server MUST support the use of the HTTP "GET" method [RFC2616] for the authorization endpoint and MAY support the use of the "POST" method as well.', async () => {
      const response = await get({
        url: "https://auth.login.yahoo.co.jp/yconnect/v2/.well-known/openid-configuration",
      })
      console.log(response.data)
      expect(response.status).toBe(200)
      const res = await post({
        url: "https://auth.login.yahoo.co.jp/yconnect/v2/token",
        headers: {
          "content-type": "application/x-www-form-urlencoded",
        },
        body: {
          client_id: "123",
          code: "code",
          grant_type: "authorization_code",
        },
      })
      var www = res.headers.get("www-authenticate")
      console.log(www)
    })
  })
})
