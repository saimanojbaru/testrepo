// Tiny typed pub/sub. Level-scoped systems subscribe on level start and the bus is
// cleared on level teardown, so nothing leaks between runs.

export type NoiseKind = 'crouch' | 'walk' | 'sprint' | 'bump' | 'door' | 'jam' | 'phone';

export interface NoiseEvent {
  x: number;
  z: number;
  radius: number;
  kind: NoiseKind;
  /** True when the player made it: only player noise raises awareness of the player. */
  fromPlayer: boolean;
}

export interface GameEvents {
  noise: NoiseEvent;
  toast: { text: string; duration?: number };
}

type Handler<T> = (payload: T) => void;

export class EventBus<M> {
  private handlers = new Map<keyof M, Set<Handler<never>>>();

  on<K extends keyof M>(type: K, handler: Handler<M[K]>): () => void {
    let set = this.handlers.get(type);
    if (!set) {
      set = new Set();
      this.handlers.set(type, set);
    }
    set.add(handler as Handler<never>);
    return () => set.delete(handler as Handler<never>);
  }

  emit<K extends keyof M>(type: K, payload: M[K]): void {
    const set = this.handlers.get(type);
    if (!set) return;
    for (const h of [...set]) (h as Handler<M[K]>)(payload);
  }

  clear(): void {
    this.handlers.clear();
  }
}

export const events = new EventBus<GameEvents>();
