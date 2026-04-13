# Graph Report - /root/.claude  (2026-04-13)

## Corpus Check
- Corpus is ~16,363 words - fits in a single context window. You may not need a graph.

## Summary
- 42 nodes · 39 edges · 11 communities detected
- Extraction: 87% EXTRACTED · 13% INFERRED · 0% AMBIGUOUS · INFERRED: 5 edges (avg confidence: 0.81)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- [[_COMMUNITY_Claude Code & Skill Integration|Claude Code & Skill Integration]]
- [[_COMMUNITY_Graphify Core Pipeline|Graphify Core Pipeline]]
- [[_COMMUNITY_Session Hooks & Config|Session Hooks & Config]]
- [[_COMMUNITY_Semantic Extraction & Caching|Semantic Extraction & Caching]]
- [[_COMMUNITY_Graph Analysis & Queries|Graph Analysis & Queries]]
- [[_COMMUNITY_Audit Trail|Audit Trail]]
- [[_COMMUNITY_Obsidian Export|Obsidian Export]]
- [[_COMMUNITY_Neo4j Export|Neo4j Export]]
- [[_COMMUNITY_SVG Export|SVG Export]]
- [[_COMMUNITY_GraphML Export|GraphML Export]]
- [[_COMMUNITY_Agent Loop|Agent Loop]]

## God Nodes (most connected - your core abstractions)
1. `Graphify Pipeline (9-Step Workflow)` - 10 edges
2. `Claude Code Documentation Map` - 6 edges
3. `SessionStart Hook Workflow (8-Step)` - 5 edges
4. `Graphify Skill` - 4 edges
5. `Semantic Extraction via Subagents` - 4 edges
6. `Community Detection and Clustering (graphify.cluster)` - 4 edges
7. `Session Start Hook Skill` - 4 edges
8. `Semantic Extraction Cache (graphify.cache)` - 3 edges
9. `Claude Code Skills System` - 3 edges
10. `Graphify Skill Reference in CLAUDE.md` - 2 edges

## Surprising Connections (you probably didn't know these)
- `Graphify Pipeline (9-Step Workflow)` --semantically_similar_to--> `SessionStart Hook Workflow (8-Step)`  [INFERRED] [semantically similar]
  ~/.claude/skills/graphify/SKILL.md → ~/.claude/skills/session-start-hook/SKILL.md
- `Session Start Hook Skill` --implements--> `Claude Code Skills System`  [INFERRED]
  ~/.claude/skills/session-start-hook/SKILL.md → ~/.claude/projects/-home-user-testrepo/dda2ba3a-00d7-4fbb-b2ee-dab2bce661cd/tool-results/toolu_01LMfepSMo3UMX2eVuvmmdSw.txt
- `Graphify Skill Reference in CLAUDE.md` --conceptually_related_to--> `Claude Code Memory (CLAUDE.md and Auto Memory)`  [INFERRED]
  ~/.claude/CLAUDE.md → ~/.claude/projects/-home-user-testrepo/dda2ba3a-00d7-4fbb-b2ee-dab2bce661cd/tool-results/toolu_01LMfepSMo3UMX2eVuvmmdSw.txt
- `Graphify Skill` --implements--> `Claude Code Skills System`  [INFERRED]
  ~/.claude/skills/graphify/SKILL.md → ~/.claude/projects/-home-user-testrepo/dda2ba3a-00d7-4fbb-b2ee-dab2bce661cd/tool-results/toolu_01LMfepSMo3UMX2eVuvmmdSw.txt
- `Graphify Skill Reference in CLAUDE.md` --references--> `Graphify Skill`  [EXTRACTED]
  ~/.claude/CLAUDE.md → ~/.claude/skills/graphify/SKILL.md

## Hyperedges (group relationships)
- **Graphify Extraction Pipeline (AST + Semantic + Cache)** — graphify_ast_extraction, graphify_semantic_extraction, graphify_cache, graphify_detect, graphify_whisper_transcription [EXTRACTED 0.95]
- **Graphify Multi-Format Export System** — graphify_html_viz, graphify_obsidian_export, graphify_neo4j_export, graphify_svg_export, graphify_graphml_export, graphify_mcp_server [EXTRACTED 0.90]
- **Claude Code Extensibility Framework** — claude_code_skills, claude_code_hooks, claude_code_plugins, claude_code_mcp, claude_code_subagents [INFERRED 0.85]

## Communities

### Community 0 - "Claude Code & Skill Integration"
Cohesion: 0.25
Nodes (9): Claude Code Documentation Map, Claude Code MCP Integration, Claude Code Memory (CLAUDE.md and Auto Memory), Claude Code Plugins System, Claude Code Skills System, Graphify Skill Reference in CLAUDE.md, MCP Stdio Server (graphify.serve), Graphify Skill (+1 more)

### Community 1 - "Graphify Core Pipeline"
Cohesion: 0.25
Nodes (8): AST Structural Extraction (graphify.extract), Community Labeling, File Detection (graphify.detect), GRAPH_REPORT.md Output, HTML Visualization (graphify.export.to_html), Graphify Pipeline (9-Step Workflow), Token Reduction Benchmark (graphify.benchmark), Whisper Transcription (graphify.transcribe)

### Community 2 - "Session Hooks & Config"
Cohesion: 0.25
Nodes (8): Claude Code Hooks System, Claude Code Settings JSON (.claude/settings.json), Claude Code on the Web, Rationale: Async Mode Latency vs Race Condition Tradeoff, Async Hook Mode, SessionStart Environment Variables (CLAUDE_PROJECT_DIR, CLAUDE_ENV_FILE, CLAUDE_CODE_REMOTE), Session Start Hook Skill, SessionStart Hook Workflow (8-Step)

### Community 3 - "Semantic Extraction & Caching"
Cohesion: 0.33
Nodes (6): Claude Code Sub-agents System, Semantic Extraction Cache (graphify.cache), Incremental Update (--update), Semantic Extraction via Subagents, Rationale: Check Cache Before Dispatching Subagents, Rationale: Why Parallel Subagent Dispatch is Mandatory

### Community 4 - "Graph Analysis & Queries"
Cohesion: 0.5
Nodes (4): Community Detection and Clustering (graphify.cluster), God Nodes Analysis (graphify.analyze.god_nodes), Graph Query (BFS/DFS Traversal), Surprising Connections Analysis

### Community 5 - "Audit Trail"
Cohesion: 1.0
Nodes (2): Honest Audit Trail (EXTRACTED/INFERRED/AMBIGUOUS), Rationale: Why Honest Audit Trail Matters

### Community 6 - "Obsidian Export"
Cohesion: 1.0
Nodes (1): Obsidian Vault Export (graphify.export.to_obsidian)

### Community 7 - "Neo4j Export"
Cohesion: 1.0
Nodes (1): Neo4j Export (graphify.export.to_cypher/push_to_neo4j)

### Community 8 - "SVG Export"
Cohesion: 1.0
Nodes (1): SVG Export (graphify.export.to_svg)

### Community 9 - "GraphML Export"
Cohesion: 1.0
Nodes (1): GraphML Export (graphify.export.to_graphml)

### Community 10 - "Agent Loop"
Cohesion: 1.0
Nodes (1): Claude Code Agent Loop

## Knowledge Gaps
- **26 isolated node(s):** `AST Structural Extraction (graphify.extract)`, `Community Labeling`, `HTML Visualization (graphify.export.to_html)`, `Obsidian Vault Export (graphify.export.to_obsidian)`, `Neo4j Export (graphify.export.to_cypher/push_to_neo4j)` (+21 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **Thin community `Audit Trail`** (2 nodes): `Honest Audit Trail (EXTRACTED/INFERRED/AMBIGUOUS)`, `Rationale: Why Honest Audit Trail Matters`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Obsidian Export`** (1 nodes): `Obsidian Vault Export (graphify.export.to_obsidian)`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Neo4j Export`** (1 nodes): `Neo4j Export (graphify.export.to_cypher/push_to_neo4j)`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `SVG Export`** (1 nodes): `SVG Export (graphify.export.to_svg)`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `GraphML Export`** (1 nodes): `GraphML Export (graphify.export.to_graphml)`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Agent Loop`** (1 nodes): `Claude Code Agent Loop`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `Graphify Pipeline (9-Step Workflow)` connect `Graphify Core Pipeline` to `Claude Code & Skill Integration`, `Session Hooks & Config`, `Semantic Extraction & Caching`, `Graph Analysis & Queries`?**
  _High betweenness centrality (0.459) - this node is a cross-community bridge._
- **Why does `SessionStart Hook Workflow (8-Step)` connect `Session Hooks & Config` to `Graphify Core Pipeline`?**
  _High betweenness centrality (0.209) - this node is a cross-community bridge._
- **Why does `Semantic Extraction via Subagents` connect `Semantic Extraction & Caching` to `Graphify Core Pipeline`?**
  _High betweenness centrality (0.201) - this node is a cross-community bridge._
- **What connects `AST Structural Extraction (graphify.extract)`, `Community Labeling`, `HTML Visualization (graphify.export.to_html)` to the rest of the system?**
  _26 weakly-connected nodes found - possible documentation gaps or missing edges._