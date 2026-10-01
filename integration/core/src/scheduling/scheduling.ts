import type { Clock, Scheduler } from "../ports.js";
export const systemClock: Clock = { now: () => Date.now() };
export const systemScheduler: Scheduler = {
  schedule(callback, ms) { const timer = setTimeout(callback, ms); return () => clearTimeout(timer); }
};
export function abortable<T>(operation: Promise<T>, signal: AbortSignal): Promise<T> {
  return new Promise((resolve, reject) => {
    const abort = () => reject(new Error("ABORTED"));
    if (signal.aborted) { operation.catch(() => undefined); abort(); return; }
    signal.addEventListener("abort", abort, { once: true });
    operation.then(resolve, reject).finally(() => signal.removeEventListener("abort", abort));
  });
}
export function delay(scheduler: Scheduler, ms: number, signal: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    if (signal.aborted) { reject(new Error("ABORTED")); return; }
    const abort = () => { cancel(); reject(new Error("ABORTED")); };
    const cancel = scheduler.schedule(() => { signal.removeEventListener("abort", abort); resolve(); }, ms);
    signal.addEventListener("abort", abort, { once: true });
  });
}
