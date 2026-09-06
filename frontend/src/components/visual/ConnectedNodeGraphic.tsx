import { useLayoutEffect, useRef } from "react";
import { gsap } from "gsap";
import no8doIcon from "@/assets/logo/no8do-icone.png";

const nodes = [[48, 62], [132, 30], [292, 48], [354, 128], [302, 218], [96, 214]];
const routes = nodes.map(([x, y]) => `M${x} ${y} Q${x} 130 200 130`);

/** Decorative brand network; it does not represent integration status. */
export function ConnectedNodeGraphic({ className = "", wordmark = false, paused = false }: { className?: string; wordmark?: boolean; paused?: boolean }) {
  const root = useRef<HTMLDivElement>(null);
  useLayoutEffect(() => {
    const element = root.current;
    if (!element || paused) return;
    const media = gsap.matchMedia();
    media.add("(prefers-reduced-motion: no-preference)", () => {
      const timeline = gsap.timeline({ repeat: -1, defaults: { ease: "sine.inOut" } });
      timeline.fromTo(".connected-node__pulse", { strokeDashoffset: 110, opacity: 0 }, { strokeDashoffset: 0, opacity: .8, duration: 2.6, stagger: .35 }, 0)
        .fromTo(".connected-node__dot", { opacity: .35 }, { opacity: 1, duration: 1.4, stagger: .18, yoyo: true, repeat: 1 }, 0)
        .fromTo(".connected-node__halo", { scale: .9, opacity: .25 }, { scale: 1.12, opacity: .65, duration: 2.4, yoyo: true, repeat: 1, transformOrigin: "center" }, 0)
        .fromTo(".connected-node__light", { xPercent: -100, opacity: 0 }, { xPercent: 100, opacity: .6, duration: 4.8 }, 0);
      let visible = false;
      const sync = () => { timeline.paused(!visible || document.hidden); };
      const observer = new IntersectionObserver(([entry]) => { visible = entry.isIntersecting; sync(); });
      observer.observe(element);
      sync();
      document.addEventListener("visibilitychange", sync);
      return () => { observer.disconnect(); document.removeEventListener("visibilitychange", sync); };
    }, element);
    return () => media.revert();
  }, [paused]);

  return <div ref={root} className={`connected-node ${wordmark ? "connected-node--wordmark" : ""} ${className}`} aria-hidden="true">
    <span className="connected-node__light" />
    <svg viewBox="0 0 400 260" fill="none">
      <ellipse className="connected-node__halo" cx="200" cy="130" rx="78" ry="66" />
      <path className="connected-node__orbit" d="M116 110 A88 78 0 0 1 264 66 M282 154 A88 78 0 0 1 146 192" />
      {routes.map((d) => <g key={d}><path className="connected-node__route" d={d} /><path className="connected-node__pulse" d={d} pathLength="100" /></g>)}
      {nodes.map(([cx, cy]) => <g key={cx}><circle className="connected-node__socket" cx={cx} cy={cy} r="9" /><circle className="connected-node__dot" cx={cx} cy={cy} r="3.5" /></g>)}
    </svg>
    <img className="connected-node__mark" src={wordmark ? "/no8do-completo.png" : no8doIcon} alt="" />
  </div>;
}
