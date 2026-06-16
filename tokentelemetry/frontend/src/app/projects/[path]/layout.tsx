"use client";

import Link from "next/link";
import { useParams, usePathname } from "next/navigation";
import { useMemo } from "react";
import {
  ChevronRight, Folder, Activity, Sparkles, ClipboardList, Settings2,
  Clock, Users, Zap,
} from "lucide-react";

import { useResource } from "@/lib/api";
import { cn } from "@/lib/cn";
import { Card, AgentBadge } from "@/components/ui";
import { ProjectProvider, type ProjectData, type SessionRow } from "./_lib/project-context";

const TABS = [
  { key: "activity", label: "Activity",  icon: Activity,      href: "activity"  },
  { key: "insights", label: "Insights",  icon: Sparkles,      href: "insights"  },
  { key: "plans",    label: "Plans",     icon: ClipboardList, href: "plans"     },
  { key: "config",   label: "Config",    icon: Settings2,     href: "config"    },
];

export default function ProjectShellLayout({ children }: { children: React.ReactNode }) {
  const params = useParams();
  const rawPath = (params?.path as string) || "";
  const decodedPath = decodeURIComponent(rawPath);
  const pathname = usePathname();

  const { data: projectsList = [], loading: projectsLoading } =
    useResource<ProjectData[]>("/projects", { initial: [] });
  const { data: allSessions = [], loading: sessionsLoading } =
    useResource<SessionRow[]>("/sessions", { initial: [] });

  const project = useMemo(
    () => projectsList.find((p) => p.path === decodedPath),
    [projectsList, decodedPath],
  );

  const sessions = useMemo(() => {
    return allSessions
      .filter((s) => s.project === decodedPath)
      .slice()
      .sort((a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime());
  }, [allSessions, decodedPath]);

  const projectName = decodedPath.split("/").pop() || "Unknown Project";
  const totalTokens = sessions.reduce((sum, s) => sum + (s.tokens?.total ?? 0), 0);
  const subagents = (project?.configured_subagent_count ?? 0) + (project?.subagent_count ?? 0);
  const plansCount = project?.plans?.length ?? 0;

  /* Determine active tab from URL segment */
  const activeKey = useMemo(() => {
    const tail = pathname.split("/").pop();
    return TABS.find((t) => t.key === tail)?.key ?? "activity";
  }, [pathname]);

  const ctxValue = {
    decodedPath,
    projectName,
    project,
    sessions,
    loading: projectsLoading || sessionsLoading,
  };

  return (
    <ProjectProvider value={ctxValue}>
      <div className="px-8 pt-6 max-w-[1600px] mx-auto pb-20">
        {/* Breadcrumb */}
        <nav aria-label="Breadcrumb" className="flex items-center gap-1.5 text-[12px] text-[var(--tt-fg-dim)] mb-4">
          <Link href="/projects" className="hover:text-[var(--tt-fg)] transition-colors">Projects</Link>
          <ChevronRight size={12} className="text-[var(--tt-fg-faint)]" />
          <span className="text-[var(--tt-fg-muted)] truncate max-w-[420px]" title={decodedPath}>
            {projectName}
          </span>
        </nav>

        {/* Project header card */}
        <Card padding="lg" className="mb-4">
          <div className="flex flex-wrap items-start justify-between gap-6">
            <div className="flex items-start gap-4 min-w-0">
              <div className="h-12 w-12 grid place-items-center rounded-[var(--tt-radius-lg)] bg-[var(--tt-brand-glow)] border border-[color:var(--tt-brand)]/25 text-[var(--tt-brand)] shrink-0">
                <Folder size={22} strokeWidth={2} />
              </div>
              <div className="min-w-0">
                <h1 className="text-[24px] leading-tight font-semibold tracking-[-0.02em] text-[var(--tt-fg)] truncate" title={projectName}>
                  {projectName}
                </h1>
                <div className="mt-1.5 inline-flex items-center gap-2 px-2 py-0.5 rounded-md font-mono text-[11px] text-[var(--tt-fg-dim)] bg-[var(--tt-sunken)] border border-[var(--tt-border)] max-w-full">
                  <span className="truncate" title={decodedPath}>{decodedPath}</span>
                </div>
                {project && project.agents.length > 0 && (
                  <div className="mt-3 flex items-center gap-1.5 flex-wrap">
                    {project.agents.map((a) => <AgentBadge key={a} agent={a} />)}
                  </div>
                )}
              </div>
            </div>

            <div className="grid grid-cols-4 gap-px rounded-[var(--tt-radius)] overflow-hidden bg-[var(--tt-border)] border border-[var(--tt-border)]">
              <HeaderStat icon={<Clock size={14} />}        label="Sessions"  value={sessions.length} />
              <HeaderStat icon={<Users size={14} />}        label="Subagents" value={subagents}       tone="purple" />
              <HeaderStat icon={<ClipboardList size={14} />} label="Plans"     value={plansCount}      tone="emerald" />
              <HeaderStat icon={<Zap size={14} />}          label="Tokens"    value={fmtNum(totalTokens)} tone="amber" />
            </div>
          </div>
        </Card>

        {/* Sticky tab strip */}
        <div className="sticky top-0 z-20 -mx-8 px-8 py-2 mb-6 bg-[var(--tt-canvas)]/85 backdrop-blur supports-[backdrop-filter]:bg-[var(--tt-canvas)]/60 border-b border-[var(--tt-border)]">
          <div className="flex items-center gap-1">
            {TABS.map((t) => {
              const I = t.icon;
              const isActive = t.key === activeKey;
              const count = t.key === "plans" ? plansCount : t.key === "activity" ? sessions.length : undefined;
              return (
                <Link
                  key={t.key}
                  href={`/projects/${rawPath}/${t.href}`}
                  className={cn(
                    "relative flex items-center gap-2 px-3.5 h-9 rounded-[var(--tt-radius)] text-[12px] font-medium transition-colors",
                    isActive
                      ? "text-[var(--tt-fg)] tt-tint-2"
                      : "text-[var(--tt-fg-muted)] hover:text-[var(--tt-fg)] hover:tt-tint-1",
                  )}
                >
                  <I size={14} className={isActive ? "text-[var(--tt-brand)]" : ""} />
                  {t.label}
                  {count !== undefined && (
                    <span className={cn(
                      "tabular text-[10px] px-1.5 h-4 grid place-items-center rounded",
                      isActive ? "bg-[var(--tt-brand-glow)] text-[var(--tt-brand)]" : "tt-tint-2 text-[var(--tt-fg-dim)]",
                    )}>
                      {count}
                    </span>
                  )}
                  {isActive && (
                    <span aria-hidden className="absolute inset-x-2.5 -bottom-2 h-[2px] rounded-full bg-[var(--tt-brand)]" />
                  )}
                </Link>
              );
            })}
          </div>
        </div>

        {/* Tab body */}
        <div>{children}</div>
      </div>
    </ProjectProvider>
  );
}

function HeaderStat({
  icon, label, value, tone = "default",
}: { icon: React.ReactNode; label: string; value: number | string; tone?: "default" | "purple" | "emerald" | "amber" }) {
  const TONE: Record<string, string> = {
    default: "text-[var(--tt-fg)]",
    purple:  "text-purple-300",
    emerald: "text-emerald-300",
    amber:   "text-amber-300",
  };
  return (
    <div className="bg-[var(--tt-panel)] px-4 py-3 min-w-[96px]">
      <div className="flex items-center gap-1.5 text-[var(--tt-fg-dim)] mb-1.5">{icon}<span className="text-[9px] font-semibold uppercase tracking-[0.18em]">{label}</span></div>
      <div className={cn("tabular text-[18px] font-semibold leading-none", TONE[tone])}>{value}</div>
    </div>
  );
}

function fmtNum(n: number) {
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`;
  if (n >= 1_000) return `${(n / 1_000).toFixed(1)}k`;
  return String(n);
}
