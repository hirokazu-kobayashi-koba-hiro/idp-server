import {Container, Paper, Stack, Typography} from "@mui/material";

const Home = () => {

    return (
        <>
            <Container maxWidth={"xs"} sx={{ m:4, alignContent: "center" }}>
                <Paper sx={{ p:4 }}>
                    <Stack spacing={4}>
                        <Typography variant={"h5"}>Home</Typography>
                    </Stack>
                </Paper>
            </Container>
        </>
    )
}

export default Home;