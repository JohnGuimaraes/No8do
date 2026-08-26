import { memo, useEffect, useRef, useState } from "react";
import { Group, Layer, Line, Path, Rect, Stage } from "react-konva";
import { type ProjectStatus } from "@/projects/projectApi";
import paperTexture from "@/assets/textura/sticky-paper-texture.png";

type Size = { width: number; height: number };

let paperTextureImage: HTMLImageElement | null = null;
let paperTexturePromise: Promise<HTMLImageElement> | null = null;

const PALETTE: Record<ProjectStatus, { base: string; adhesive: string; underside: string; edge: string; shadow: string }> = {
  IDEA: { base: "#f6e89a", adhesive: "#fbf1bd", underside: "#f2eddb", edge: "#cdbb65", shadow: "rgba(75, 61, 23, 0.32)" },
  PLANNING: { base: "#f3e3a0", adhesive: "#faf0bf", underside: "#f1ecd9", edge: "#c7b46c", shadow: "rgba(75, 61, 23, 0.3)" },
  ACTIVE: { base: "#f1dc8e", adhesive: "#f8ebaf", underside: "#f0ead4", edge: "#c7aa51", shadow: "rgba(75, 61, 23, 0.32)" },
  BLOCKED: { base: "#eaa39b", adhesive: "#f3b8ae", underside: "#efe6df", edge: "#bd776e", shadow: "rgba(78, 35, 31, 0.3)" },
  PAUSED: { base: "#b7c6d3", adhesive: "#cbd8e1", underside: "#e8ecec", edge: "#839baa", shadow: "rgba(35, 52, 63, 0.3)" },
  DONE: { base: "#d6dcc4", adhesive: "#e3e8d4", underside: "#ebece2", edge: "#a9b391", shadow: "rgba(43, 53, 33, 0.28)" },
};

export const StickyNoteSurface = memo(function StickyNoteSurface({ status, noteId }: { status: ProjectStatus; noteId: string }) {
  const hostRef = useRef<HTMLDivElement>(null);
  const textureRef = useRef<HTMLImageElement | null>(null);
  const [size, setSize] = useState<Size>({ width: 0, height: 0 });
  const [textureReady, setTextureReady] = useState(false);

  useEffect(() => {
    const host = hostRef.current;
    if (!host) return;
    const observer = new ResizeObserver(([entry]) => {
      const width = Math.round(entry.contentRect.width);
      const height = Math.round(entry.contentRect.height);
      setSize((current) => (current.width === width && current.height === height ? current : { width, height }));
    });
    observer.observe(host);
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    let active = true;

    void loadPaperTexture().then((image) => {
      if (!active) return;
      textureRef.current = image;
      setTextureReady(true);
    });

    return () => {
      active = false;
    };
  }, []);

  if (size.width === 0 || size.height === 0) return <div ref={hostRef} className="sticky-note-surface" aria-hidden="true" />;

  const palette = PALETTE[status];
  const inset = 1;
  const points = [inset, 2, size.width - 2, 1, size.width - 1, size.height - 3, size.width - 3, size.height - 1, 2, size.height - 2, 1, 3];
  const offset = getTextureOffset(noteId);
  const fold = getFold(noteId, size);
  const foldPath = getFoldPath(fold, size);

  return (
    <div ref={hostRef} className="sticky-note-surface" aria-hidden="true">
      <Stage width={size.width} height={size.height} pixelRatio={1} listening={false}>
        <Layer listening={false}>
          <Line points={points} closed fill={palette.base} stroke={palette.edge} strokeWidth={1} shadowColor={palette.shadow} shadowBlur={8} shadowOffset={{ x: 1, y: 4 }} shadowOpacity={0.36} listening={false} />
          {textureReady && textureRef.current ? <Line points={points} closed fillPatternImage={textureRef.current} fillPatternRepeat="repeat" fillPatternOffset={offset} fillPatternScale={{ x: 0.34, y: 0.34 }} opacity={0.44} globalCompositeOperation="multiply" listening={false} /> : null}
          <Group clipFunc={(context) => { context.beginPath(); context.moveTo(inset, 2); context.lineTo(size.width - 2, 1); context.lineTo(size.width - 1, size.height - 3); context.lineTo(size.width - 3, size.height - 1); context.lineTo(2, size.height - 2); context.lineTo(1, 3); context.closePath(); }} listening={false}>
            <Rect x={1} y={1} width={size.width - 2} height={size.height - 2} fillRadialGradientStartPoint={{ x: 5, y: 5 }} fillRadialGradientEndPoint={{ x: 5, y: 5 }} fillRadialGradientEndRadius={Math.max(size.width, size.height)} fillRadialGradientColorStops={[0, "rgba(255, 255, 255, 0.13)", 1, "rgba(255, 255, 255, 0)"]} listening={false} />
            <Rect x={1} y={1} width={size.width - 2} height={20} fill={palette.adhesive} opacity={0.42} listening={false} />
            {textureReady && textureRef.current ? <Rect x={1} y={1} width={size.width - 2} height={20} fillPatternImage={textureRef.current} fillPatternRepeat="repeat" fillPatternOffset={offset} fillPatternScale={{ x: 0.34, y: 0.34 }} opacity={0.16} globalCompositeOperation="multiply" listening={false} /> : null}
            <Path data={foldPath} fill="rgba(35, 28, 18, 0.1)" shadowColor={palette.shadow} shadowBlur={5} shadowOffset={{ x: 0, y: 2 }} shadowOpacity={0.36} listening={false} />
            <Path data={foldPath} fill={palette.underside} stroke={palette.edge} strokeWidth={0.6} listening={false} />
            {textureReady && textureRef.current ? <Path data={foldPath} fillPatternImage={textureRef.current} fillPatternRepeat="repeat" fillPatternOffset={offset} fillPatternScale={{ x: 0.34, y: 0.34 }} opacity={0.09} globalCompositeOperation="multiply" listening={false} /> : null}
          </Group>
          <Line points={[3, size.height - 2, size.width - 3, size.height - 1]} stroke={palette.edge} strokeWidth={0.6} opacity={0.34} listening={false} />
        </Layer>
      </Stage>
    </div>
  );
});

function getTextureOffset(noteId: string) {
  let hash = 0;
  for (let index = 0; index < noteId.length; index += 1) hash = (hash * 31 + noteId.charCodeAt(index)) >>> 0;
  return { x: hash % 160, y: (hash >>> 8) % 160 };
}

function getFold(noteId: string, size: Size) {
  const hash = getHash(noteId);
  const foldSize = Math.round(Math.min(20, Math.max(14, Math.min(size.width, size.height) * 0.1)));
  return { side: hash % 2 === 0 ? "right" : "left", size: foldSize } as const;
}

function getFoldPath(fold: ReturnType<typeof getFold>, size: Size) {
  const x = fold.side === "right" ? size.width : 0;
  const direction = fold.side === "right" ? -1 : 1;
  const startX = x + direction * fold.size;
  const curveX = x + direction * fold.size * 0.42;
  const creaseY = size.height - fold.size;

  return `M ${startX} ${size.height - 1} C ${curveX} ${size.height - fold.size * 0.72}, ${curveX} ${size.height - fold.size * 0.36}, ${x + direction} ${creaseY} L ${x + direction} ${size.height - 1} Z`;
}

function getHash(value: string) {
  let hash = 0;
  for (let index = 0; index < value.length; index += 1) hash = (hash * 31 + value.charCodeAt(index)) >>> 0;
  return hash;
}

function loadPaperTexture() {
  if (paperTextureImage) return Promise.resolve(paperTextureImage);
  if (paperTexturePromise) return paperTexturePromise;

  paperTexturePromise = new Promise((resolve, reject) => {
    const image = new Image();
    image.onload = () => {
      paperTextureImage = image;
      resolve(image);
    };
    image.onerror = () => reject(new Error("Unable to load sticky paper texture."));
    image.src = paperTexture;
  });

  return paperTexturePromise;
}
