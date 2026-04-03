import { Router } from "express";
import {
  listPlugins,
  getPlugin,
  getPluginSkill,
  publishPlugin,
  deletePlugin,
} from "../controllers/pluginsController";

const router = Router();

router.get("/", listPlugins);
router.get("/:name/skill", getPluginSkill);  // must be before /:name
router.get("/:name", getPlugin);
router.post("/", publishPlugin);
router.delete("/:name", deletePlugin);

export default router;
