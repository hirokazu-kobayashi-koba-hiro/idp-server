export const getUserAgentData = async (navigator) => {
  if (navigator.userAgentData) {
    const ua = await navigator.userAgentData.getHighEntropyValues([
      "architecture",
      "model",
      "platform",
      "platformVersion",
    ]);
    return ua;
  }
  return null;
};

export const parseUserAgent = (userAgent) => {
  let os = "Unknown OS";
  let browser = "Unknown Browser";
  let deviceType = "Unknown Device";

  if (/Windows/i.test(userAgent)) os = "Windows";
  if (/Macintosh|Mac OS/i.test(userAgent)) os = "Mac";
  if (/Android/i.test(userAgent)) os = "Android";
  if (/iPhone|iPad|iPod/i.test(userAgent)) os = "iOS";

  if (/Chrome/i.test(userAgent)) browser = "Chrome";
  if (/Safari/i.test(userAgent) && !/Chrome/i.test(userAgent))
    browser = "Safari";
  if (/Firefox/i.test(userAgent)) browser = "Firefox";
  if (/Edge/i.test(userAgent)) browser = "Edge";

  if (/Mobile|Android|iPhone/i.test(userAgent)) deviceType = "Mobile";
  if (/Mac|Windows/i.test(userAgent)) deviceType = "PC";

  return { os, browser, deviceType };
};
