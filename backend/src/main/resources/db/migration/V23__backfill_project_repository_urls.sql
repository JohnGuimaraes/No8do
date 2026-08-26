update projects project
set repository_url = trim(technical_info.repository_url)
from project_technical_info technical_info
where technical_info.project_id = project.id
  and nullif(trim(project.repository_url), '') is null
  and nullif(trim(technical_info.repository_url), '') is not null;
