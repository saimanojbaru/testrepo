import './style.css';
import { Game } from './core/Game';

const app = document.querySelector<HTMLDivElement>('#app');
if (!app) throw new Error('#app container missing');
if (new URLSearchParams(location.search).has('lineup')) {
  // Debug-only character sign-off page; loaded lazily so it never ships in the game path.
  void import('./debug/Lineup').then((m) => m.showLineup(app));
} else if (new URLSearchParams(location.search).has('gnmcast')) {
  // Debug casting sheet for the photoreal heads (?gnmcast&ids=ramesh,kavita:smile).
  void import('./debug/GnmCast').then((m) => m.showGnmCast(app));
} else {
  new Game(app);
}
