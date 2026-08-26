import { type IdeaType } from "@/ideas/ideaApi";

export function IdeaTypeGlyph({ type }: { type: IdeaType }) {
  return <span className={`idea-type-glyph idea-type-glyph-${type.toLowerCase()}`} aria-hidden="true"><span className="idea-type-glyph-mark idea-type-glyph-mark-a" /><span className="idea-type-glyph-mark idea-type-glyph-mark-b" /><span className="idea-type-glyph-mark idea-type-glyph-mark-c" /></span>;
}
