alter table users
    add column password_hash varchar(255);

update users
set password_hash = '$2a$10$7EqJtq98hPqEX7fNZaFWoOhiXcp81ppJR7Ytcj4fFKYg5N0rE9C2i'
where password_hash is null;

alter table users
    alter column password_hash set not null;

alter table users
    add column enabled boolean not null default true;
