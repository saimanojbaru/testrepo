import { Router } from "express";
import {
  listPlugins,
  getPlugin,
  publishPlugin,
  deletePlugin,
} from "../controllers/pluginsController";

const router = Router();

router.get("/", listPlugins);
router.get("/:name", getPlugin);
router.post("/", publishPlugin);
router.delete("/:name", deletePlugin);

export default router;
