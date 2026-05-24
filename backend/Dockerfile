version: "3.9"

services:
  db:
    image: postgres:16-alpine
    container_name: style_db
    environment:
      POSTGRES_DB: ${POSTGRES_DB}
      POSTGRES_USER: ${DB_USER}
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    ports:
      - "${POSTGRES_PORT}:5432"
    volumes:
      - pg_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${DB_USER}"]
      interval: 10s
      timeout: 5s
      retries: 5

  backend:
    build:
      context: ./backend
      dockerfile: Dockerfile
    container_name: style_backend
    env_file:
      - ./backend/.env
    environment:
      DATABASE_URL: jdbc:postgresql://db:5432/${POSTGRES_DB}
      DB_USER: ${DB_USER}
      DB_PASSWORD: ${DB_PASSWORD}
      PORT: ${APP_PORT}
    ports:
      - "${APP_PORT}:8080"
    depends_on:
      db:
        condition: service_healthy
    restart: unless-stopped

volumes:
  pg_data: