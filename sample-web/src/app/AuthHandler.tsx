"use client";

import { signIn, useSession } from "next-auth/react";
import React, { useEffect } from "react";
import { useRouter, usePathname } from "next/navigation";
import { Loading } from "@/components/Loading";
import { sleep } from "@/functions/sleep";

export default function AuthHandler({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  const router = useRouter();
  const { data: session, status } = useSession();
  const pathname = usePathname();

  const goToPage = async () => {
    await sleep(500);
    if (session) {
      console.log(session);
    }
    if (pathname && pathname !== "/") {
      router.push(pathname);
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
    signIn("idp-server");
  }, [router, status]);

  if (status === "loading") {
    return <Loading />;
  }

  return <>{children}</>;
}
