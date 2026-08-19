package com.no8do.api.idea;

import com.no8do.api.project.ProjectResponse;

public record IdeaConvertResponse(
        IdeaResponse idea,
        ProjectResponse project
) {
}
