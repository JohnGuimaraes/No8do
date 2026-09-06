import { useLayoutEffect, useRef } from "react";
import { gsap } from "gsap";

const networkPaths = [
  "M104 730 C248 596 330 540 472 512",
  "M472 512 C612 452 732 390 874 300",
  "M472 512 C630 610 770 694 982 728",
  "M874 300 C1014 190 1144 194 1306 258",
  "M982 728 C1120 680 1236 600 1338 480",
  "M874 300 C906 432 930 578 982 728",
  "M104 730 C146 554 136 394 218 260",
  "M218 260 C376 194 544 184 684 228",
];

const nodes = [
  { cx: 104, cy: 730, r: 7 }, { cx: 218, cy: 260, r: 6 }, { cx: 472, cy: 512, r: 18, core: true },
  { cx: 684, cy: 228, r: 5, reserved: true }, { cx: 874, cy: 300, r: 8 }, { cx: 982, cy: 728, r: 7 },
  { cx: 1306, cy: 258, r: 6, reserved: true }, { cx: 1338, cy: 480, r: 5, reserved: true },
];

export function ReplayNetworkBackground() {
  const rootRef = useRef<SVGSVGElement>(null);

  useLayoutEffect(() => {
    if (!rootRef.current || window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;
    const context = gsap.context(() => {
      const lines = gsap.utils.toArray<SVGPathElement>("[data-replay-network-line]");
      const pulses = gsap.utils.toArray<SVGPathElement>("[data-replay-network-pulse]");
      const activeNodes = gsap.utils.toArray<SVGCircleElement>("[data-replay-network-node]:not([data-reserved])");
      const arcs = gsap.utils.toArray<SVGPathElement>("[data-replay-network-arc]");
      const timeline = gsap.timeline({ defaults: { ease: "power3.inOut" } });

      lines.forEach((line) => {
        const length = line.getTotalLength();
        gsap.set(line, { strokeDasharray: length, strokeDashoffset: length });
      });
      timeline
        .fromTo(arcs, { autoAlpha: 0, scale: .94, transformOrigin: "50% 50%" }, { autoAlpha: 1, scale: 1, duration: .34, stagger: .05 })
        .to(lines, { strokeDashoffset: 0, duration: .58, stagger: .055 }, "<.08")
        .fromTo(activeNodes, { autoAlpha: 0, scale: .55, transformOrigin: "50% 50%" }, { autoAlpha: 1, scale: 1, duration: .26, stagger: .045 }, "<.18")
        .fromTo(pulses, { autoAlpha: 0, strokeDashoffset: 72 }, { autoAlpha: .9, strokeDashoffset: 0, duration: .52, stagger: .1 }, "<.1")
        .to(activeNodes, { scale: 1.14, duration: .13, stagger: { each: .055, from: "edges" }, yoyo: true, repeat: 1, ease: "power2.out" }, "<.23")
        .to(arcs, { autoAlpha: .35, scale: 1.025, duration: .28, stagger: .04 }, "<.12");
    }, rootRef);
    return () => context.revert();
  }, []);

  return <svg ref={rootRef} className="replay-network" viewBox="0 0 1440 900" preserveAspectRatio="xMidYMid slice" aria-hidden="true">
    <g className="replay-network__field">
      {networkPaths.map((path, index) => <path key={path} d={path} className="replay-network__line" data-replay-network-line data-network-index={index} />)}
      <path d={networkPaths[1]} className="replay-network__pulse" data-replay-network-pulse />
      <path d={networkPaths[4]} className="replay-network__pulse replay-network__pulse--secondary" data-replay-network-pulse />
      <path d="M388 426 C436 354 526 344 584 402 C628 448 622 526 560 560" className="replay-network__arc" data-replay-network-arc />
      <path d="M824 188 C938 124 1060 164 1090 272" className="replay-network__arc replay-network__arc--outer" data-replay-network-arc />
      <path d="M906 642 C1028 594 1146 622 1208 716" className="replay-network__arc replay-network__arc--outer" data-replay-network-arc />
      <circle cx="472" cy="512" r="47" className="replay-network__core-ring" />
      <circle cx="472" cy="512" r="30" className="replay-network__core-ring replay-network__core-ring--inner" />
      {nodes.map((node) => <g key={`${node.cx}-${node.cy}`} className={node.core ? "replay-network__node replay-network__node--core" : "replay-network__node"} data-replay-network-node={true} data-reserved={node.reserved || undefined}>
        <circle cx={node.cx} cy={node.cy} r={node.r + (node.core ? 5 : 3)} className="replay-network__node-halo" />
        <circle cx={node.cx} cy={node.cy} r={node.r} className="replay-network__node-dot" />
      </g>)}
      <circle cx="1162" cy="186" r="2.5" className="replay-network__point" />
      <circle cx="1180" cy="704" r="2" className="replay-network__point" />
      <circle cx="324" cy="656" r="2" className="replay-network__point" />
    </g>
  </svg>;
}
