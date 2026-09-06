import { useLayoutEffect, useRef } from "react";
import { gsap } from "gsap";
import { DrawSVGPlugin } from "gsap/DrawSVGPlugin";
import { MotionPathPlugin } from "gsap/MotionPathPlugin";
import { CustomEase } from "gsap/CustomEase";

gsap.registerPlugin(DrawSVGPlugin, MotionPathPlugin, CustomEase);
CustomEase.create("acervoBreeze", "M0,0 C0.18,0.05 0.25,0.42 0.5,0.5 0.72,0.57 0.8,0.96 1,1");

/** Shared vector scene: the entry resolves into the library's marginal illustration. */
export function AcervoLibraryScene() {
  return <svg className="acervo-library-scene" viewBox="0 0 1000 760" fill="none" aria-hidden="true" focusable="false">
    <g data-library-rule><path d="M70 688H950 M720 80V135 M930 636V702" /><path d="M370 570C350 340 480 158 718 158" /></g>
    <g data-library-books>
      {[0, 1, 2].map((i) => <g key={i} transform={`translate(${i * -12} ${i * 29})`}><path d="M628 584Q616 596 628 608H891Q881 596 891 584Z M650 584Q642 596 650 608 M869 588H886 M866 593H883 M866 598H883 M869 603H886" /></g>)}
    </g>
    <g data-library-open>
      <path d="M172 665Q270 589 391 665Q472 601 577 665L582 677Q468 648 392 681Q288 653 164 677Z M392 665V681" />
      <g data-library-page><path d="M185 659Q288 607 390 669 M204 647Q303 615 386 665 M404 666Q471 626 556 656 M410 661Q473 617 540 647" /></g>
      <path data-library-ink d="M425 638C447 606 469 629 454 638S461 607 482 619 519 626 527 609" />
    </g>
    <g data-library-owl>
      <path d="M665 280Q653 234 641 218C660 253 725 247 757 309C784 251 844 258 862 222Q857 254 844 278 M656 267Q623 329 641 405C624 459 595 512 578 562Q646 522 701 441Q748 382 720 345 M840 272Q874 332 834 409Q823 468 787 516L777 570 M653 406Q722 365 746 432Q751 497 719 569" />
      <path d="M646 279C681 278 735 297 757 344C778 300 819 284 847 282 M656 291C648 342 692 366 738 374 M838 292C846 341 814 362 776 379 M738 374L757 398L776 379 M656 420Q687 403 717 425C697 481 640 521 597 548 M650 443Q635 492 601 531 M717 425Q741 472 704 548 M719 569L710 582H751 M777 570L793 582H815" />
      <path className="acervo-scene-gold" data-library-eyes d="M684 314A14 14 0 1 0 711 317 M799 317A14 14 0 1 0 826 314 M701 437Q678 488 634 517" />
      <path d="M710 271Q760 254 810 274 M662 384Q708 383 733 412 M797 402Q813 444 787 485" />
    </g>
    <g className="acervo-scene-gold"><path d="M876 167A26 26 0 0 1 876 215A24 24 0 0 0 876 167" /><circle cx="719" cy="158" r="2" /></g>
    {[0, 1].map((i) => <g data-library-feather key={i} opacity="0"><g data-library-barb><path d="M0 0C-12-35-5-70 28-99C35-57 27-22 0 0Z M-5 14L24-88 M1-15L-7-35 M7-34L-4-55 M12-52L4-72 M6-30L24-45 M12-50L28-64 M18-70L29-80" /></g></g>)}
  </svg>;
}

export function AcervoEditorialBackground() {
  const root = useRef<HTMLDivElement>(null);
  useLayoutEffect(() => {
    if (!root.current) return;
    const element = root.current;
    const media = gsap.matchMedia();
    media.add("(prefers-reduced-motion: no-preference)", () => {
      const feathers = element.querySelectorAll("[data-library-feather]");
      const timeline = gsap.timeline({ repeat: -1, repeatDelay: 5, paused: true });
      feathers.forEach((feather, index) => {
        const start = index === 0 ? 3 : 23;
        timeline.fromTo(feather, { opacity: 0 }, { opacity: .8, duration: 2 }, start)
          .to(feather, { motionPath: { path: index === 0 ? [{ x: 938, y: 165 }, { x: 902, y: 310 }, { x: 959, y: 442 }, { x: 904, y: 630 }] : [{ x: 105, y: 290 }, { x: 66, y: 400 }, { x: 113, y: 540 }, { x: 80, y: 659 }], curviness: 1.4 }, duration: index === 0 ? 12 : 10, ease: "acervoBreeze" }, start)
          .fromTo(feather.querySelector("g"), { rotation: -16 }, { rotation: 18, y: 4, transformOrigin: "center", duration: 3, repeat: 3, yoyo: true, ease: "sine.inOut" }, start)
          .to(feather, { opacity: 0, duration: 2 }, start + (index === 0 ? 10 : 8));
      });
      timeline.fromTo("[data-library-ink]", { drawSVG: "0%" }, { drawSVG: "100%", duration: 3, ease: "sine.inOut" }, 17)
        .to("[data-library-page]", { y: -3, duration: 2, yoyo: true, repeat: 1, ease: "sine.inOut" }, 37)
        .fromTo("[data-library-eyes]", { opacity: .4 }, { opacity: 1, duration: 2, repeat: 1, yoyo: true }, 43);
      let visible = false;
      const sync = () => { timeline.paused(!visible || document.hidden); };
      const observer = new IntersectionObserver(([entry]) => { visible = entry.isIntersecting; sync(); });
      observer.observe(element);
      document.addEventListener("visibilitychange", sync);
      return () => { observer.disconnect(); document.removeEventListener("visibilitychange", sync); };
    }, element);
    return () => media.revert();
  }, []);
  return <div ref={root} className="acervo-editorial-background" aria-hidden="true"><AcervoLibraryScene /></div>;
}

export function animateAcervoEntry(root: HTMLElement, complete: () => void) {
  const media = gsap.matchMedia();
  media.add({ motion: "(prefers-reduced-motion: no-preference)", reduced: "(prefers-reduced-motion: reduce)" }, (context) => {
    if (context.conditions?.reduced) { complete(); return; }
    const overlay = root.querySelector(".library-entry-overlay");
    if (!overlay) return;
    const select = gsap.utils.selector(overlay);
    gsap.timeline({ defaults: { ease: "power2.inOut" }, onComplete: complete })
      .from(select("[data-library-rule] path"), { drawSVG: "0%", duration: .3 }, 0)
      .from(select("[data-library-books] path"), { drawSVG: "0%", duration: .35 }, .1)
      .from(select("[data-library-books] > g"), { x: 25, y: 12, stagger: .04, duration: .3 }, .15)
      .from(select("[data-library-open]"), { scaleX: .08, transformOrigin: "50% 100%", duration: .4 }, .25)
      .from(select("[data-library-page]"), { y: 14, duration: .3 }, .4)
      .to(select("[data-library-feather]")[0], { opacity: .8, motionPath: { path: [{ x: 430, y: 630 }, { x: 480, y: 599 }, { x: 530, y: 610 }], curviness: 1.5 }, duration: .45, ease: "acervoBreeze" }, .42)
      .from(select("[data-library-ink]"), { drawSVG: "0%", duration: .4 }, .45)
      .from(select("[data-library-owl] path"), { drawSVG: "0%", stagger: .025, duration: .35 }, .65)
      .from(select(".library-entry-overlay__title"), { y: 15, opacity: 0, duration: .3 }, .45)
      .to(select("svg"), { scale: .88, xPercent: 8, transformOrigin: "75% 50%", duration: .35 }, 1)
      .to(root.querySelector(".knowledge-environment__content"), { opacity: 1, duration: .35 }, 1.05)
      .to(overlay, { clipPath: "inset(0 0 100% 0)", opacity: 0, duration: .35 }, 1.05);
  }, root);
  return () => media.revert();
}
