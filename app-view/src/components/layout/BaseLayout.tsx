import { Box, Container, Paper, useMediaQuery, useTheme } from "@mui/material";

export const BaseLayout = ({ children }: { children: React.ReactNode }) => {
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down("sm"));

  return (
    <Box sx={{ height: "100vh" }}>
      <Container maxWidth={isMobile ? "xs" : "xs"}>
        {isMobile ? (
          <Box mt={2}>{children}</Box>
        ) : (
          <Paper sx={{ p: 3, boxShadow: 1, borderRadius: 3 }}>{children}</Paper>
        )}
      </Container>
    </Box>
  );
};
