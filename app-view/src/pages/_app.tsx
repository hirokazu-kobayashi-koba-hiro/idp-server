import { AppProps } from "next/app";
import { CssBaseline } from "@mui/material";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createContext, useContext, useState } from "react";

const queryClient = new QueryClient();

export const backendUrl =
  process.env.NEXT_PUBLIC_BACKEND_URL ||
  "https://idp-server-0d10773f8944.herokuapp.com";

interface AppContextType {
  id: string | null;
  setId: (id: string) => void;
  tenantId: string | null;
  setTenantId: (id: string) => void;
  userId: string | null;
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

  return (
    <>
      <AppContext.Provider
        value={{ id, setId, tenantId, setTenantId, userId, setUserId }}
      >
        <CssBaseline />
        <QueryClientProvider client={queryClient}>
          <Component {...pageProps} />
        </QueryClientProvider>
      </AppContext.Provider>
    </>
  );
}
