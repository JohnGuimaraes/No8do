type EntityCoverProps = {
  id: string;
  kind?: "project" | "idea" | "library" | "workspace";
  className?: string;
};

const VARIANTS = ["node-grid", "layers", "orbit", "mesh", "connections", "planes"];

function hashVariant(value: string) {
  let hash = 0;
  for (let index = 0; index < value.length; index += 1) hash = (hash * 31 + value.charCodeAt(index)) >>> 0;
  return VARIANTS[hash % VARIANTS.length];
}

export function EntityCover({ id, kind = "project", className = "" }: EntityCoverProps) {
  return (
    <div className={`entity-cover entity-cover--${kind} entity-cover--${hashVariant(`${kind}-${id}`)} ${className}`} aria-hidden="true">
      <span className="entity-cover__orbit" />
      <span className="entity-cover__node entity-cover__node--one" />
      <span className="entity-cover__node entity-cover__node--two" />
      <span className="entity-cover__node entity-cover__node--three" />
    </div>
  );
}
