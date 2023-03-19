export const get = async ({ url, headers }) => {
  try {
    const response = await fetch(url, {
      method: "GET",
      headers: {
        "content-type": "application/json",
        ...headers,
      },
      mode: "cors",
    })
    return {
      status: response.status,
      headers: response.headers,
      data: await response.json(),
    }
  } catch (e) {
    return {
      status: 500,
      data: e.messages,
    }
  }
}

export const post = async ({ url, headers, body }) => {
  try {
    const response = await fetch(url, {
      method: "POST",
      body: body ? JSON.stringify(body) : undefined,
      headers: {
        ...headers,
      },
      mode: "cors",
    })
    return {
      status: response.status,
      headers: response.headers,
      data: await response.json(),
    }
  } catch (e) {
    return {
      status: 500,
      data: e.messages,
    }
  }
}
