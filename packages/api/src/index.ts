import express from "express";
import pluginsRouter from "./routes/plugins";

const app = express();
const PORT = process.env.PORT ?? 3000;

app.use(express.json());

app.get("/health", (_req, res) => {
  res.json({ status: "ok" });
});

app.use("/plugins", pluginsRouter);

app.listen(PORT, () => {
  console.log(`CCPI Registry API running on http://localhost:${PORT}`);
});

export default app;
