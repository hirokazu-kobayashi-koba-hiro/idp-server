"use client";

import { signIn, useSession } from "next-auth/react";
import React, { useEffect } from "react";
import { useRouter, usePathname } from "next/navigation";
import { Loading } from "@/components/Loading";
import { sleep } from "@/functions/sleep";

/**
 * Pages that choose how to sign in themselves. The cross-site demo offers both sign-ins side by
 * side, so sending the visitor to one of them automatically would defeat the page.
 */
const SELF_SIGN_IN_PATHS = ["/cross-site-demo"];

export default function AuthHandler({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  const router = useRouter();
  const { status } = useSession();
  const pathname = usePathname();

  const goToPage = async () => {
    await sleep(500);
    if (pathname && pathname !== "/") {
      // pathname にはクエリが含まれない。落とすと、戻り先に付いた結果（?linked= など）が消える。
      router.push(`${pathname}${window.location.search}${window.location.hash}`);
      return;
    }
    router.push("/home");
  };

  useEffect(() => {
    if (status === "authenticated") {
      goToPage();
      return;
    }
    if (status === "loading") {
      return;
    }
    if (pathname && SELF_SIGN_IN_PATHS.includes(pathname)) {
      return;
    }
    signIn("idp-server");
  }, [router, status]);

  if (status === "loading") {
    return <Loading />;
  }

  return <>{children}</>;
}
