-- Multi-database initialization script for local development
-- Database-per-service pattern simulated on a single PostgreSQL instance

SELECT 'Creating database: users_db' AS status;
CREATE DATABASE users_db;
GRANT ALL PRIVILEGES ON DATABASE users_db TO orderflow;

SELECT 'Creating database: orders_db' AS status;
CREATE DATABASE orders_db;
GRANT ALL PRIVILEGES ON DATABASE orders_db TO orderflow;

SELECT 'Creating database: inventory_db' AS status;
CREATE DATABASE inventory_db;
GRANT ALL PRIVILEGES ON DATABASE inventory_db TO orderflow;

SELECT 'Creating database: notification_db' AS status;
CREATE DATABASE notification_db;
GRANT ALL PRIVILEGES ON DATABASE notification_db TO orderflow;
