import { useEffect, useRef } from "react";
import gsap from "gsap";

export type PathPhase = "INITIAL" | "VALIDATED" | "WORKSPACE_SELECTED" | "READY" | "APPROVING" | "APPROVED" | "CONSUMED" | "DENIED" | "EXPIRED" | "ERROR";
const phaseText: Record<PathPhase, string> = {
  INITIAL: "Aguardando código", VALIDATED: "Código localizado", WORKSPACE_SELECTED: "Workspace selecionado",
  READY: "Agent selecionado", APPROVING: "Aprovando conexão", APPROVED: "Conexão aprovada",
  CONSUMED: "Integração conectada", DENIED: "Conexão recusada", EXPIRED: "Solicitação expirada", ERROR: "Conexão interrompida",
};
export function ConnectionPath({ phase, integration, workspace, agent }: {
  phase: PathPhase; integration: string; workspace?: string; agent?: string;
}) {
  const root = useRef<HTMLDivElement>(null);
  const previous = useRef("");
  const count = phase === "INITIAL" ? 0 : phase === "VALIDATED" ? 2 : phase === "WORKSPACE_SELECTED" ? 3 : 4;
  const interrupted = ["DENIED", "EXPIRED", "ERROR"].includes(phase);
  useEffect(() => {
    const element = root.current;
    if (!element) return;
    const media = gsap.matchMedia();
    const signature = phase;
    const changed = previous.current !== signature;
    previous.current = signature;
    media.add("(prefers-reduced-motion: no-preference)", () => {
      if (!changed || interrupted || phase === "INITIAL" || phase === "APPROVING" || phase === "CONSUMED") return;
      const segment = phase === "VALIDATED" ? 0 : phase === "WORKSPACE_SELECTED" ? 1 : 2;
      const final = phase === "APPROVED";
      const pulse = element.querySelector(".connection-path__pulse");
      const timeline = gsap.timeline();
      const respond = (index: number, at: number) => {
        const halo = element.querySelector(`.connection-path__halo[data-node='${index}']`);
        const rim = element.querySelector(`[data-node='${index}'] .connection-path__rim`);
        const core = element.querySelector(`[data-node='${index}'] .connection-path__core, [data-node='${index}'] .connection-path__hub-core`);
        const satellites = element.querySelectorAll(`[data-node='${index}'] .connection-path__satellite`);
        timeline.fromTo(core, { opacity: .2 }, { opacity: 1, duration: .08 }, at)
          .fromTo(rim, { opacity: .35 }, { opacity: 1, duration: .12 }, at + .06)
          .fromTo(halo, { opacity: 0 }, { opacity: .22, duration: .1 }, at + .08)
          .to(halo, { opacity: 0, duration: .1 }, at + .18);
        if (satellites.length) timeline.fromTo(satellites, { opacity: .45 }, { opacity: 1, duration: .08 }, at + .16)
          .to(satellites, { opacity: .65, duration: .06 }, at + .24);
      };
      const highlight = (index: number, at: number, duration: number) => {
        const line = element.querySelector(`.connection-path__highlight[data-segment='${index}']`);
        timeline.fromTo(line, { strokeDashoffset: 12, opacity: 1 }, { strokeDashoffset: -60, duration, ease: "power1.inOut" }, at)
          .to(line, { opacity: 0, duration: .06 }, at + duration);
      };
      timeline.set(pulse, { attr: { cy: final ? 45 : 45 + segment * 100 }, opacity: 1 }, 0)
        .to(pulse, { attr: { cy: final ? 345 : 145 + segment * 100 }, duration: final ? .72 : .35, ease: "none" }, 0)
        .to(pulse, { opacity: 0, duration: .06 }, final ? .72 : .35);
      if (final) {
        [0, 1, 2, 3].forEach(index => respond(index, index * .24));
        [0, 1, 2].forEach(index => highlight(index, index * .24, .24));
      } else {
        if (segment === 0) respond(0, 0);
        if (segment === 1) respond(1, 0);
        highlight(segment, 0, .35);
        respond(segment + 1, .35);
      }
    }, element);
    return () => media.revert();
  }, [phase, interrupted]);
  const labels = ["Integração", "No8do", "Workspace", "Agent"];
  const details = [integration === "Integração" ? undefined : integration, undefined, workspace, agent];
  return <div ref={root} className="connection-path" data-phase={phase}>
    <p className="sr-only" role="status">{phaseText[phase]}. Integração: {integration}.{workspace ? ` Workspace: ${workspace}.` : ""}{agent ? ` Agent: ${agent}.` : ""}</p>
    <svg viewBox="0 -130 360 550" fill="none" className="connection-path__drawing" aria-hidden="true">
      <text className="connection-path__progress" x="180" y="-12" textAnchor="middle">{phaseText[phase]}</text>
      <path d="M174 104 Q180 101 186 104 L211 118 Q218 122 218 130 V160 Q218 168 211 172 L186 186 Q180 189 174 186 L149 172 Q142 168 142 160 V130 Q142 122 149 118 Z" className="connection-path__hub-backplate" />
      <ellipse cx="180" cy="145" rx="57" ry="31" className="connection-path__orbit connection-path__secondary" transform="rotate(-35 180 145)" />
      {[0, 1, 2, 3].map(index => <circle key={index} cx="180" cy={45 + index * 100} r={index === 1 ? 36 : 23}
        className="connection-path__halo" data-node={index} />)}
      {[0, 1, 2].map(index => <g key={index} className="connection-path__connector" data-active={!interrupted && count > index + 1}>
        <line x1="180" x2="180" y1={[68, 186, 268][index]} y2={[104, 222, 322][index]} className="connection-path__line" />
        <circle cx="180" cy={[68, 186, 268][index]} r="2" className="connection-path__anchor" />
        <circle cx="180" cy={[104, 222, 322][index]} r="2" className="connection-path__anchor" />
      </g>)}
      {[0, 1, 2].map(index => <line key={index} x1="180" x2="180" y1={[68, 186, 268][index]} y2={[104, 222, 322][index]}
        className="connection-path__highlight" data-segment={index} />)}
      {labels.map((label, index) => <g key={index} className="connection-path__node" data-node={index}
        data-state={interrupted ? "INTERRUPTED" : index >= count ? "INACTIVE" : phase === "VALIDATED" && index === 1 ? "ACTIVE" : "COMPLETED"}>
        <g className="connection-path__shape">
          {index === 1 ? <path className="connection-path__hub" d="M175 115 Q180 112 185 115 L203 125 Q209 129 209 136 V154 Q209 161 203 165 L185 175 Q180 178 175 175 L157 165 Q151 161 151 154 V136 Q151 129 157 125 Z" /> : <circle cx="180" cy={45 + index * 100} r="15" />}
          {index === 1 ? <path className="connection-path__rim" d="M174 109 Q180 106 186 109 L207 121 Q214 125 214 133 V157 Q214 165 207 169 L186 181 Q180 184 174 181 L153 169 Q146 165 146 157 V133 Q146 125 153 121 Z" /> : <circle className="connection-path__rim" cx="180" cy={45 + index * 100} r="21" />}
          {index === 1 ? <>
            <path className="connection-path__hub-core" d="M180 125 C168 125 166 136 180 145 C194 154 192 165 180 165 C168 165 166 154 180 145 C194 136 192 125 180 125 Z" />
            <g className="connection-path__secondary">
              <path className="connection-path__circuit" d="M151 145 H128 M203 126 Q215 122 220 110" />
              <circle className="connection-path__satellite" cx="128" cy="145" r="3" />
              <circle className="connection-path__satellite connection-path__satellite--accent" cx="220" cy="110" r="2.5" />
            </g>
          </> : <>
            <circle className="connection-path__core" cx="180" cy={45 + index * 100} r="5" />
            <path className="connection-path__circuit" d={`M159 ${45 + index * 100} h-6`} />
            <circle className="connection-path__satellite" cx="153" cy={45 + index * 100} r="1.75" />
          </>}
          <circle className="connection-path__marker" cx={index === 1 ? 180 : 194} cy={index === 1 ? 176 : 31 + index * 100} r="2.5" />
          <path className="connection-path__interruption" d={`M176 ${41 + index * 100} l8 8 m0 -8 -8 8`} />
        </g>
        <text x="230" y={50 + index * 100}>
          <tspan>{label}</tspan>
        </text>
        {details[index] ? <foreignObject x="230" y={57 + index * 100} width="116" height="22">
          <div className="connection-path__detail" title={details[index]}>{details[index]}</div>
        </foreignObject> : null}
      </g>)}
      <circle className="connection-path__pulse" cx="180" cy="45" r="2.5" />
      <path className="connection-path__caption-link" d="M180 368 V378 M158 378 H202" />
      <circle className="connection-path__caption-anchor" cx="180" cy="378" r="2" />
      <text className="connection-path__caption" x="180" y="395" textAnchor="middle">
        <tspan x="180">Identidade própria.</tspan><tspan x="180" dy="18">Acesso controlado.</tspan>
      </text>
    </svg>
  </div>;
}
