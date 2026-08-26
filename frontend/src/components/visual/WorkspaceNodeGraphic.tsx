import no8doIcon from "@/assets/logo/no8do-icone.png";

export function WorkspaceNodeGraphic({ className = "" }: { className?: string }) {
  return (
    <div className={`no8do-node ${className}`.trim()} aria-hidden="true">
      <div className="no8do-node-scene">
        <span className="no8do-node-frame no8do-node-frame--outer" />
        <span className="no8do-node-frame no8do-node-frame--inner" />
        <span className="no8do-node-orbit no8do-node-orbit--top" />
        <span className="no8do-node-orbit no8do-node-orbit--bottom" />
        <span className="no8do-node-link no8do-node-link--north" />
        <span className="no8do-node-link no8do-node-link--west" />
        <span className="no8do-node-link no8do-node-link--east" />
        <span className="no8do-node-link no8do-node-link--south" />
        <span className="no8do-node-core" />
        <span className="no8do-node-point no8do-node-point--north" />
        <span className="no8do-node-point no8do-node-point--west" />
        <span className="no8do-node-point no8do-node-point--east" />
        <span className="no8do-node-point no8do-node-point--south" />
        <span className="no8do-node-module no8do-node-module--north" />
        <span className="no8do-node-module no8do-node-module--east" />
      </div>
      <span className="no8do-node-icon"><img src={no8doIcon} alt="" /></span>
    </div>
  );
}
