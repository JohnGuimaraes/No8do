alter table projects
    add column cover_image_key varchar(255),
    add column cover_image_updated_at timestamp with time zone;
