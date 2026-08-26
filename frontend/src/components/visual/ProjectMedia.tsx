import { useEffect, useState } from "react";
import no8doLogo from "@/assets/logo/no8do-logo.png";
import { getProjectCoverUrl, type Project } from "@/projects/projectApi";

type ProjectMediaProps = { workspaceId: string; project: Project; alt: string };

export function ProjectMedia({ workspaceId, project, alt }: ProjectMediaProps) {
  const source = getProjectCoverUrl(workspaceId, project) || no8doLogo;
  const [imageSource, setImageSource] = useState(source);

  useEffect(() => {
    setImageSource(source);
  }, [source]);

  return (
    <div className="project-media">
      <img
        src={imageSource}
        alt={alt}
        loading="lazy"
        decoding="async"
        className={imageSource === no8doLogo ? "project-media__fallback" : undefined}
        onError={() => setImageSource(no8doLogo)}
      />
    </div>
  );
}
