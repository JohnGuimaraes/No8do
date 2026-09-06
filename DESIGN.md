# Design Context

## Product

No8do is a private collaborative workspace for operational context, projects, references, and reusable technical knowledge.

## Visual Direction

- Technical canvas, editorial hierarchy, restrained surfaces, and functional movement.
- Semantic surface tokens from `frontend/src/index.css`; no independent color system.
- `DESENVOLVIMENTO` is active project work. `CONHECIMENTO` contains Acervo and Replays.
- Navigation uses compact, ruled semantic groups: Visão geral, Desenvolvimento, and Conhecimento.
- Replay is technical knowledge: Problem, Context, and Solution lead; evidence remains compact and Tags stay distinct from Stack.
- Replay detail has local Visão geral, Código, and Histórico modes. Código only presents fenced blocks extracted from existing Markdown, with their source and language.
- The reference concepts are a restrained technical hierarchy, dense metadata, dividers, and purposeful micro-motion; they do not introduce external tokens or copied components.
- The Replay index uses an editorial lead, a compact operational metric rail, a ruled query surface, and numbered technical artifacts instead of a generic CRUD list.
- Replays opens directly in the immersive Hub. The Workspace overview owns the live recent-artifact spotlight, cycling through at most five real Replays; its controls pause the finite cycle on hover/focus and may open the selected Replay without inventing a new route.
- Entering Replays is one finite GSAP network transition: core, route rule, nodes, pulses and the `REPLAYS` label resolve over the already-mounted Hub, which becomes visible only in the final phase. Reduced motion bypasses timing without changing navigation.
- Inside the immersive environment, the Replay Hub leads with a small last-used surface (falling back to update time) and an inline Explorer. Explorer facets are derived only from Replay data: Stack, Type, recorded problem signals and tags, and fenced-code languages.
- The immersive Hub prioritizes exploration over recency: Header, search, filters and metrics lead into Explorer, then a local Grade/List artifact catalog. Grade is the default technical scan surface; local ordering uses existing timestamps, usage counts and titles only, with no new API behavior.
- Explorer disclosure uses a structural rule, count response, and short opacity/translate reveal. It never replaces search, status filters, creation, or detail navigation.
- Levo Studio informed the use of deliberate vertical rhythm, broad typographic lead-ins, small monospaced labels, and full-width dividers. shadcn/ui Blocks informs code headers, language labels, and copyable implementation snippets.
- The immersive Hub uses the full available canvas inside its responsive shell padding; only long-form copy is constrained for readability.
- Replay detail is a ruled technical artifact: the title and evidence rail establish hierarchy, Overview uses semantic vertical rules, Code retains real fenced-block provenance and copy controls, and History is a compact temporal trail. Its short reveals and line growth always collapse under reduced motion.
- Replays uses a sparse SVG network field derived from the No8do mark's core, orbiting nodes, and asymmetrical links: one reserved core, eight nodes at most, thin paths, quiet arcs, and restrained radial accents. The field is decorative, does not claim live agent connectivity, and introduces no canvas, WebGL, or continuous background motion.
- Portal-to-Hub entry is one finite GSAP sequence: the access mark activates a core, the route line crosses, the network resolves, and the Portal dissolves while the Hub mounts immediately beneath it. Reduced motion skips the sequence without changing navigation.
- The Portal spotlight uses a small contextual network: the active Replay is its sole active center; Codex, Claude, MCP, Extensão and Automação are named only as prepared or em breve reserved nodes, never as connected integrations.
- Acervo and Replays are full-viewport knowledge environments inside the existing Workspace route. Each owns one internal scroll surface and a compact `Workspace` return header; the Workspace shell remains mounted underneath and resumes through that return action.
- The Workspace overview exposes Replays as a full-width recent-knowledge entry: it reuses the five-item spotlight and its finite motion, opens a selected artifact directly in the immersive detail view, and sends `Ver todos` directly to the Hub.
- The Workspace overview keeps Replays inside the top composition as a compact dashboard-native recent-knowledge card: both general access actions enter the Hub, while the rotating artifact remains descriptive. One compact Workspace Node card retains the No8do graphic as its visual focus and embeds the real-data knowledge map for Replays, Acervo, Ideias and project states.
- The Dashboard identity field treats the existing No8do artwork as a restrained compositional layer, with static light and node accents rather than a marketing banner or continuous visual effect. Operational sections initially reveal only a short scan of real items and expose their remaining existing items through local expansion.
- The Hub catalog is intentionally local and bounded: it initially reveals ten artifacts, expands in groups of ten, and resets that local window whenever the search, status, Explorer facet, ordering or Grade/List view changes. Explorer facet selection closes its disclosure while retaining a compact removable filter marker beside the catalog.
- Acervo has its own finite document-layer entrance: Books, `ACERVO`, a resolving rule and three quiet document planes reveal the existing fullscreen content without changing its data or actions.

## Runtime Mapping

Tokens are owned by `frontend/src/index.css` CSS custom properties and exposed through `frontend/tailwind.config.js`. This context adds no runtime tokens.

## Overview refinement

The current overview brief supersedes earlier static identity-field and single-spotlight directions: the Workspace Node occupies the upper right spanning metrics and the identity band; Replays spans the next full row. The newest real Replay stays anchored beside a second artifact cycling through the other recent items every 3.3 seconds. General access opens the Hub. The shared ConnectedNodeGraphic uses existing primary/card tokens, the original mark and six SVG nodes, with sparse continuous signals explicitly requested for this dashboard only. Motion pauses manually, offscreen, in hidden tabs, and under reduced motion; it never represents a connected external agent. Operational disclosures initially show two child entries and do not infer record counts from decorative or empty-state children.

The right-hand recent-Replays panel is now an editor-style snippet preview rather than a brand diagram. It prioritizes fenced code from the loaded recent Replays, with explicitly labeled unsaved illustrative examples selected from Replay themes (Java, SQL, React or automation), falling back to a JSON metadata example when none exists. It shows nine lines with provenance, language and source title in the established mono stack. Snippet cards slide in opposing directions over 440 ms after a 3-second hold; the overview pause, hover/focus, visibility and reduced-motion controls govern autoplay. Workspace Node and the identity band retain their graphics.

The approved snippet transition stays at 440 ms with a 5.5-second reading hold. The central recent Replay has its own 400 ms opposing block slide while the newest stays stable. Acervo keeps all four recent entries available in a compact editorial rail (10rem minimum instead of 14.5rem), with a quiet book signature on wide screens. Its independent background uses a small outline owl watermark, two page outlines and a slow ink stroke; only pages and ink animate over 10 seconds. Motion stops offscreen, in hidden tabs, under reduced motion, or by the local background pause control. Categories, search, item actions and fullscreen navigation keep their existing owners.


Acervo: biblioteca digital viva / sabedoria / memória / escrita / conhecimento preservado. Cena SVG editorial de livros, coruja e penas nas margens, azul profundo com âmbar pontual. Entrada de 1,4s; motion ambiental espaçado com pausa automática e reduced-motion, sem controle visível. Replays mantém rede técnica / código / conexões / reutilização.
