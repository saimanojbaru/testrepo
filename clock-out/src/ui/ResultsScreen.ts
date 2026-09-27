import type { Ending, ExcuseDef, LevelData, NPCDef, Outcome } from '../data/types';
import { HR_REPORT, pick } from '../data/dialogueLines';
import type { OfficeMemory } from '../dialogue/OfficeMemory';
import { EXCUSES } from '../data/excuses';

// End-of-level screen. Escapes get stars and a breakdown; getting caught gets an
// HR incident report generated from what you actually said this run, because the
// report is the punchline.

export interface ExcuseLog {
  excuse: ExcuseDef;
  npc: NPCDef;
  outcome: Outcome;
  blurted: boolean;
  followUp: string | null;
  pivotText: string | null;
  atClock: string;
}

export interface RunSummary {
  level: LevelData;
  ending: Ending;
  time: number;
  stars: number;
  log: ExcuseLog[];
  repBefore: string;
  repAfter: string;
  newLegends: ExcuseDef[];
  caughtBy?: NPCDef;
  caughtReason?: 'dialogue' | 'chase' | 'deadline' | 'fled';
  flavor: string;
  hasNext: boolean;
}

const ENDING_TITLES: Record<Ending, [string, string]> = {
  CLEAN: ['CLEAN EXIT', 'Nobody saw a thing. You were never here. You were barely here before.'],
  ESCAPED: ['ESCAPED', 'You talked your way out. The office will be talking about it too.'],
  LEGEND: ['LEGEND', 'That should not have worked. It worked. It is going in the newsletter.'],
  PROMOTION: ['PROMOTION', '"Good initiative." Ramesh sir holds the lift for you. Then lets it go. You ride alone.'],
  CAUGHT: ['CAUGHT', '"Quick connect" with Ramesh sir. It was not quick. It was barely a connect.'],
};

export class ResultsScreen {
  private root: HTMLDivElement;

  constructor(parent: HTMLElement) {
    this.root = document.createElement('div');
    this.root.className = 'screen results hidden';
    parent.append(this.root);
  }

  hide(): void {
    this.root.classList.add('hidden');
  }

  show(s: RunSummary, memory: OfficeMemory, actions: { onRetry: () => void; onNext: () => void; onMenu: () => void }): void {
    const [title, sub] = ENDING_TITLES[s.ending];
    const r = this.root;
    r.innerHTML = '';
    r.className = `screen results ending-${s.ending.toLowerCase()}`;
    const banner = document.createElement('div');
    banner.className = 'results-banner';
    banner.innerHTML = `<div class="results-level">${esc(s.level.name)}</div><h1>${title}</h1><p>${esc(sub)}</p>`;
    r.append(banner);
    if (s.flavor) {
      const f = document.createElement('p');
      f.className = 'results-flavor';
      f.textContent = s.flavor;
      r.append(f);
    }

    const cols = document.createElement('div');
    cols.className = 'results-cols';
    const stats = document.createElement('div');
    stats.className = 'results-stats';
    const rows: Array<[string, string]> = [
      ['Time', `${formatTime(s.time)} (par ${formatTime(s.level.parTime)})`],
      ['Rating', s.ending === 'CAUGHT' ? '—' : '★'.repeat(s.stars) + '☆'.repeat(3 - s.stars)],
      ['Excuses used', String(s.log.length)],
      ['Reputation', s.repBefore === s.repAfter ? s.repAfter : `${s.repBefore} → ${s.repAfter}`],
    ];
    stats.innerHTML = rows.map(([k, v]) => `<div class="stat"><span>${k}</span><strong>${esc(v)}</strong></div>`).join('');
    if (s.newLegends.length) {
      const leg = document.createElement('div');
      leg.className = 'legend-banner';
      leg.innerHTML = `<strong>NEW LEGEND</strong> ${s.newLegends.map((e) => `“${esc(e.text)}”`).join(' ')}`;
      stats.append(leg);
    }
    cols.append(stats);

    if (s.ending === 'CAUGHT') {
      const rep = document.createElement('div');
      rep.className = 'hr-report';
      rep.innerHTML = hrReport(s, memory);
      cols.append(rep);
    } else if (s.log.length) {
      const list = document.createElement('div');
      list.className = 'excuse-log';
      list.innerHTML = '<h3>What you said</h3>' + s.log.map((l) =>
        `<div class="log-row"><span class="log-npc">${esc(l.npc.name.split(' ')[0])}</span><span class="log-text">“${esc(l.excuse.text)}”</span><span class="log-out out-${l.outcome.toLowerCase()}">${l.outcome}</span></div>`).join('');
      cols.append(list);
    }
    r.append(cols);

    const btns = document.createElement('div');
    btns.className = 'results-buttons';
    const mk = (label: string, cls: string, fn: () => void) => {
      const b = document.createElement('button');
      b.className = `btn ${cls}`;
      b.textContent = label;
      b.addEventListener('click', fn);
      btns.append(b);
    };
    if (s.ending !== 'CAUGHT' && s.hasNext) mk('Next level', 'primary', actions.onNext);
    mk(s.ending === 'CAUGHT' ? 'Try again' : 'Replay', s.ending === 'CAUGHT' ? 'primary' : '', actions.onRetry);
    mk('Main menu', '', actions.onMenu);
    r.append(btns);
  }
}

function hrReport(s: RunSummary, memory: OfficeMemory): string {
  const by = s.caughtBy;
  const reason = {
    dialogue: 'Attempted departure with insufficient justification.',
    chase: 'Running. Indoors. Away from a colleague.',
    fled: 'Attempted to exit the premises while being addressed by name.',
    deadline: "Failure to attend a 'Quick Connect :)' while physically present in the building.",
  }[s.caughtReason ?? 'dialogue'];
  const id = `HR-${String(10000 + Math.floor(Math.random() * 89999))}`;
  const events: string[] = [];
  for (const l of s.log) {
    let line = `At ${l.atClock}, employee told ${esc(l.npc.name)} ${quote(l.excuse.text)}${l.blurted ? ' (witnesses describe this as “blurted”)' : ''}${l.blurted || !endsSentence(l.excuse.text) ? '.' : ''}`;
    if (l.followUp) {
      line += ` When asked ${quote(l.followUp)}, employee ${l.pivotText ? `replied ${quote(l.pivotText)}${endsSentence(l.pivotText) ? '' : '.'}` : 'said nothing and stared at the carpet.'}`;
    }
    events.push(`<li>${line}</li>`);
  }
  if (s.caughtReason === 'fled') events.push(`<li>Employee was observed reaching for the door handle while ${esc(by?.name ?? 'a colleague')} said “hey” four times, each louder.</li>`);
  if (s.caughtReason === 'chase') events.push(`<li>Employee was observed moving at a velocity inconsistent with “grabbing a coffee.”</li>`);
  if (s.caughtReason === 'deadline') events.push(`<li>At 1:15 PM an Outlook invite landed. Employee was, unfortunately, still here.</li>`);
  events.push(`<li>${esc(pick(HR_REPORT.observations))}</li>`);

  const hist = memory.history();
  const top = hist[0];
  const topExcuse = top ? EXCUSES.find((e) => e.id === top[0]) : undefined;
  const prior = memory.timesCaught - 1;
  const recs = [...HR_REPORT.recommendations].sort(() => Math.random() - 0.5).slice(0, 2);
  const note = by ? HR_REPORT.archetypeNotes[by.archetype] ?? '' : '';
  return `
    <div class="hr-head"><strong>INCIDENT REPORT</strong><span>${id}</span></div>
    <div class="hr-meta">
      <div><span>Date</span>Today. Again.</div>
      <div><span>Subject</span>You (Software Engineer II, desk by the plant)</div>
      <div><span>Reporting party</span>${by ? `${esc(by.name)}, ${esc(by.role)}` : 'The building'}</div>
      <div><span>Location</span>${esc(s.level.name)}</div>
      <div><span>Nature of incident</span>${esc(reason)}</div>
    </div>
    <h4>Statement of events</h4>
    <ol>${events.join('')}</ol>
    <h4>Prior history</h4>
    <p>${prior > 0 ? `${prior} prior incident${prior > 1 ? 's' : ''} on file.` : 'No prior incidents on file. Until now.'}
    ${topExcuse ? ` Most-used excuse: “${esc(topExcuse.text)}” (${top[1]} use${top[1] > 1 ? 's' : ''}).` : ''}</p>
    ${note ? `<p class="hr-note">${esc(note)}</p>` : ''}
    <h4>Recommended action</h4>
    <ul>${recs.map((r) => `<li>${esc(r)}</li>`).join('')}</ul>
    <div class="hr-sign">Filed by Priya Menon, HR Business Partner.<br><em>This was not a formal conversation.</em></div>`;
}

function quote(s: string): string {
  return `“${esc(s)}”`;
}

/** Quoted lines that already end in punctuation don't get a second full stop after the quote. */
function endsSentence(s: string): boolean {
  return /[.!?…]$/.test(s.trim());
}

export function formatTime(sec: number): string {
  const m = Math.floor(sec / 60);
  const s = Math.floor(sec % 60);
  return `${m}:${String(s).padStart(2, '0')}`;
}

export function esc(s: string): string {
  return s.replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c]!);
}
