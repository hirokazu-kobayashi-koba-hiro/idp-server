import { AppProps } from "next/app";
import { CssBaseline } from "@mui/material";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createContext, useContext, useState } from "react";

const queryClient = new QueryClient();

// Use empty string for same-origin deployment (via nginx proxy at /auth-views/)
// This ensures AUTH_SESSION cookies are sent with SameSite=Lax
export const backendUrl = process.env.NEXT_PUBLIC_BACKEND_URL ?? "";

interface AppContextType {
  id: string | null;
  setId: (id: string) => void;
  tenantId: string | null;
  setTenantId: (id: string) => void;
  userId: string | null;
  email: string | null;
  setEmail: (email: string) => void;
  setUserId: (id: string) => void;
}

const AppContext = createContext<AppContextType | undefined>(undefined);

export const useAppContext = () => {
  const context = useContext(AppContext);
  if (!context) {
    throw new Error("undefined app context");
  }
  return context;
};

export default function App({ Component, pageProps }: AppProps) {
  const [id, setId] = useState<string>("");
  const [tenantId, setTenantId] = useState<string>("");
  const [userId, setUserId] = useState<string>("");
  const [email, setEmail] = useState<string>("");

  return (
    <>
      <AppContext.Provider
        value={{ id, setId, tenantId, setTenantId, userId, setUserId,  email, setEmail }}
      >
        <CssBaseline />
        <QueryClientProvider client={queryClient}>
          <Component {...pageProps} />
        </QueryClientProvider>
      </AppContext.Provider>
    </>
  );
}
