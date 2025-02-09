import { Backdrop, Box, CircularProgress } from "@mui/material";
import { useEffect } from "react";
import { sleep } from "@/functions/sleep";

export const Loading = () => {
  useEffect(() => {
    const wait = async () => {
      await sleep(1000);
    };
    wait();
  }, []);

  return (
    <>
      <Box
        sx={{
          display: "flex",
          flexDirection: "column",
          alignItems: "center",
          gap: 2,
        }}
      >
        <Backdrop
          sx={{
            color: "#fff",
            zIndex: (theme) => theme.zIndex.drawer + 1,
          }}
          open={true}
        >
          <CircularProgress color="inherit" />
        </Backdrop>
      </Box>
    </>
  );
};
