import axios from "axios";
import https from "https";
import fs from "fs";

// Create HTTPS agent for mTLS
const createMtlsAgent = ({ certPath, keyPath }) => {
  return new https.Agent({
    cert: fs.readFileSync(certPath),
    key: fs.readFileSync(keyPath),
    rejectUnauthorized: false, // Allow self-signed server certificates (development only)
  });
};

// Create axios client for mTLS
const createMtlsClient = ({ certPath, keyPath }) => {
  const httpsAgent = createMtlsAgent({ certPath, keyPath });
  return axios.create({
    httpsAgent,
    withCredentials: true,
  });
};

export const mtlsGet = async ({ url, headers, certPath, keyPath }) => {
  const client = createMtlsClient({ certPath, keyPath });

  try {
    return await client.get(url, {
      maxRedirects: 0,
      headers,
      withCredentials: true,
    });
  } catch (e) {
    return e.response ? e.response : e;
  }
};

export const mtlsPost = async ({ url, headers, body, certPath, keyPath }) => {
  const client = createMtlsClient({ certPath, keyPath });

  try {
    return await client.post(url, body, {
      headers: {
        "Content-Type": "application/x-www-form-urlencoded",
        ...headers,
      },
      maxRedirects: 0,
      withCredentials: true,
    });
  } catch (e) {
    return e.response ? e.response : e;
  }
};

export const mtlsPostWithJson = async ({
  url,
  headers,
  body,
  certPath,
  keyPath,
}) => {
  const client = createMtlsClient({ certPath, keyPath });

  try {
    return await client.post(url, body, {
      headers,
      withCredentials: true,
    });
  } catch (e) {
    return e.response ? e.response : e;
  }
};

export const mtlsPutWithJson = async ({
  url,
  headers,
  body,
  certPath,
  keyPath,
}) => {
  const client = createMtlsClient({ certPath, keyPath });

  try {
    return await client.put(url, body, {
      headers,
      withCredentials: true,
    });
  } catch (e) {
    return e.response ? e.response : e;
  }
};

export const mtlsPatchWithJson = async ({
  url,
  headers,
  body,
  certPath,
  keyPath,
}) => {
  const client = createMtlsClient({ certPath, keyPath });

  try {
    return await client.patch(url, body, {
      headers,
      withCredentials: true,
    });
  } catch (e) {
    return e.response ? e.response : e;
  }
};

export const mtlsDeletion = async ({ url, headers, certPath, keyPath }) => {
  const client = createMtlsClient({ certPath, keyPath });

  try {
    return await client.delete(url, {
      headers,
      withCredentials: true,
    });
  } catch (e) {
    return e.response ? e.response : e;
  }
};

export const createCustomMtlsClient = ({ certPath, keyPath }) => {
  const client = createMtlsClient({ certPath, keyPath });

  return {
    get: ({ url, headers }) => mtlsGet({ url, headers, certPath, keyPath }),
    post: ({ url, headers, body }) => mtlsPost({ url, headers, body, certPath, keyPath }),
    postWithJson: ({ url, headers, body }) => mtlsPostWithJson({ url, headers, body, certPath, keyPath }),
    putWithJson: ({ url, headers, body }) => mtlsPutWithJson({ url, headers, body, certPath, keyPath }),
    patchWithJson: ({ url, headers, body }) => mtlsPatchWithJson({ url, headers, body, certPath, keyPath }),
    deletion: ({ url, headers }) => mtlsDeletion({ url, headers, certPath, keyPath }),
  };
};
