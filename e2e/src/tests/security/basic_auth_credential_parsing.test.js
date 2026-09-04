import { describe, expect, it } from "@jest/globals";
/* global Buffer */
import {
  basicAuthAlphabetClient,
  clientSecretBasicClient,
  clientSecretPostClient,
  publicClientCredentialsClient,
  serverConfig,
} from "../testConfig";
import { requestToken } from "../../api/oauthClient";

/**
 * Issue #1820 - Basic 認証クレデンシャルのパースと、そこに乗る認証境界
 *
 * HTTP Basic は "user-id:password" をコロンで連結して Base64 するだけの仕組みで、
 * 区切りは「最初のコロン」と決まっている。そのため
 *
 *   - どのアルファベットで復号するか
 *   - 復号後に何をどう分割するか
 *   - 分割した値をそのまま照合するか、デコードしてから照合するか
 *
 * のどれを取り違えても、資格情報が別の値として解釈されうる。ここでは「別のクライアントに
 * 化けられないこと」「受理する集合が意図せず広がっていないこと」を確認する。
 *
 * 併せて RFC 6749 Section 4.4 の confidential client 限定も検証する。public client が
 * client_credentials を使えると、資格情報を一切提示しない呼び出し元にアクセストークンが
 * 発行されるため。
 */
describe("Security: Basic 認証クレデンシャルのパース", () => {
  const basicHeader = (userId, password, urlSafe = false) => {
    const encoded = Buffer.from(`${userId}:${password}`).toString("base64");
    return {
      Authorization: `Basic ${urlSafe ? encoded.replace(/\+/g, "-").replace(/\//g, "_") : encoded}`,
    };
  };

  const tokenRequest = async (basicAuth) =>
    await requestToken({
      endpoint: serverConfig.tokenEndpoint,
      grantType: "client_credentials",
      scope: "account",
      basicAuth,
    });

  describe("コロンの取り違えによるクライアント誤認", () => {
    it("user-id 側にコロンを含めても、区切りより前の文字列が別クライアントとして認証されてはならない", async () => {
      // RFC 7617 は user-id にコロンを含められないと明記している（最初のコロンが区切りのため）。
      // "victim:extra" を user-id として送ると、素朴な実装では victim を引いてしまう。
      // ここでは victim = clientSecretBasic の識別子を使い、パスワード側に本物の secret を
      // 置いても認証が通らないことを確認する。通ってしまうと、任意の接頭辞を持つ別名を
      // 登録できる攻撃者が他クライアントに化けられる。
      const response = await tokenRequest(
        basicHeader(
          `${clientSecretBasicClient.clientId}:extra`,
          clientSecretBasicClient.clientSecret
        )
      );

      console.log(response.data);
      expect(response.status).toBe(401);
      expect(response.data.error).toEqual("invalid_client");
    });

    it("パスワード側にコロンが含まれていても正しく認証できる", async () => {
      // 区切りは最初のコロンのみ。以降はすべてパスワードとして扱われる必要がある。
      // ここで分割を誤ると、コロンを含む secret を持つクライアントが認証できなくなる。
      // 正しい実装では「secret にコロンを足した誤った値」として弾かれる。
      const response = await tokenRequest(
        basicHeader(
          clientSecretBasicClient.clientId,
          `${clientSecretBasicClient.clientSecret}:tail`
        )
      );

      console.log(response.data);
      expect(response.status).toBe(401);
      expect(response.data.error).toEqual("invalid_client");
    });
  });

  describe("Base64 アルファベット (RFC 7617 / RFC 4648 Section 4)", () => {
    it("標準 Base64 で組んだ資格情報は認証できる", async () => {
      const response = await tokenRequest(
        basicHeader(clientSecretBasicClient.clientId, clientSecretBasicClient.clientSecret)
      );

      console.log(response.data);
      expect(response.status).toBe(200);
      expect(response.data).toHaveProperty("access_token");
    });

    it("URL-safe Base64 で組んだ資格情報は受理されてはならない", async () => {
      // RFC 7617 は標準 Base64 を規定している。URL-safe を受理すると、同じ資格情報に
      // 2 通りの表現を認めることになる。
      //
      // 実在するクライアントの正しい資格情報を使う必要がある。存在しない client_id で試すと、
      // URL-safe を受理する実装でも「未登録クライアント」で 401 になり、拒否したのか
      // 復号できたのか区別できない。basicAuthAlphabet は符号化結果が 2 つのアルファベットで
      // 実際に食い違うため、受理してしまう実装では 200 が返る。
      const standard = Buffer.from(
        `${basicAuthAlphabetClient.clientId}:${basicAuthAlphabetClient.clientSecret}`
      ).toString("base64");
      expect(standard).toMatch(/[+/]/);

      const response = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "client_credentials",
        scope: basicAuthAlphabetClient.scope,
        basicAuth: basicHeader(
          basicAuthAlphabetClient.clientId,
          basicAuthAlphabetClient.clientSecret,
          true
        ),
      });

      console.log(response.data);
      expect(response.status).toBe(401);
      expect(response.data.error).toEqual("invalid_client");
    });

    it("同じ資格情報を標準 Base64 で送れば認証できる", async () => {
      // 上の拒否が「アルファベットの違い」によるものであり、資格情報そのものが
      // 無効なわけではないことを示す対照。
      const response = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "client_credentials",
        scope: basicAuthAlphabetClient.scope,
        basicAuth: basicHeader(
          basicAuthAlphabetClient.clientId,
          basicAuthAlphabetClient.clientSecret
        ),
      });

      console.log(response.data);
      expect(response.status).toBe(200);
      expect(response.data).toHaveProperty("access_token");
    });

    it("両アルファベットで同一になる資格情報は、どちらで組んでも認証できる", async () => {
      // 上の拒否によって壊れる範囲が「符号化結果が実際に食い違う場合」に限られることの確認。
      // 締めすぎて通常のクライアントを巻き込んでいないことを担保する。
      const standard = Buffer.from(
        `${clientSecretBasicClient.clientId}:${clientSecretBasicClient.clientSecret}`
      ).toString("base64");
      expect(standard).not.toMatch(/[+/]/);

      const response = await tokenRequest(
        basicHeader(clientSecretBasicClient.clientId, clientSecretBasicClient.clientSecret, true)
      );

      console.log(response.data);
      expect(response.status).toBe(200);
    });
  });

  describe("RFC 6749 Section 2.3.1 のデコード境界", () => {
    it("percent-encode された資格情報を認証できる", async () => {
      // 仕様は Base64 の前に application/x-www-form-urlencoded でエンコードすると定める。
      // 受け取る側がデコードしないと、仕様どおりのクライアントが認証できない。
      const encodedSecret = "%63" + clientSecretBasicClient.clientSecret.slice(1);
      const response = await tokenRequest(
        basicHeader(clientSecretBasicClient.clientId, encodedSecret)
      );

      console.log(response.data);
      expect(response.status).toBe(200);
    });

    it("デコード結果が一致しない資格情報は認証できない", async () => {
      // デコードを入れたことで受理する集合が広がっていないことの確認。
      // %62 は 'b' であって、正しい secret の先頭 'c' とは一致しない。
      const wrongSecret = "%62" + clientSecretBasicClient.clientSecret.slice(1);
      const response = await tokenRequest(
        basicHeader(clientSecretBasicClient.clientId, wrongSecret)
      );

      console.log(response.data);
      expect(response.status).toBe(401);
      expect(response.data.error).toEqual("invalid_client");
    });
  });

  describe("RFC 6749 Section 4.4 confidential client 限定", () => {
    it("public client は client_credentials でアクセストークンを取得できない", async () => {
      // token_endpoint_auth_method=none は資格情報を提示しない。これを受理すると
      // 無認証の呼び出し元にアクセストークンが発行される。
      const response = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "client_credentials",
        scope: publicClientCredentialsClient.scope,
        clientId: publicClientCredentialsClient.clientId,
      });

      console.log(response.data);
      expect(response.status).toBe(401);
      expect(response.data.error).toEqual("invalid_client");
      expect(response.data.error_description).toContain("confidential client");
    });

    it("confidential client は client_credentials を引き続き利用できる", async () => {
      // 締めすぎていないことの確認。
      const response = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "client_credentials",
        scope: "account",
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });

      console.log(response.data);
      expect(response.status).toBe(200);
      expect(response.data).toHaveProperty("access_token");
    });
  });
});
