-- Crea las bases y los usuarios de user-service y ticket-service.
-- Corre una sola vez, cuando el volumen pgdata esta vacio.
\getenv users_pass USERS_DB_PASSWORD
\getenv tickets_pass TICKETS_DB_PASSWORD

CREATE USER users_user WITH PASSWORD :'users_pass';
CREATE DATABASE users_db OWNER users_user;
REVOKE CONNECT ON DATABASE users_db FROM PUBLIC;

CREATE USER tickets_user WITH PASSWORD :'tickets_pass';
CREATE DATABASE tickets_db OWNER tickets_user;
REVOKE CONNECT ON DATABASE tickets_db FROM PUBLIC;