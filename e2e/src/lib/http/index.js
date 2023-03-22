import axios from "axios"
export const get = async ({ url, headers }) => {
  try {
    return await axios.get(url)
  } catch (e) {
    return {
      status: 500,
      data: e,
    }
  }
}

export const post = async ({ url, headers, body }) => {
  try {
    const response = await fetch(url, {
      method: "POST",
      body: body ? JSON.stringify(body) : undefined,
      headers,
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
