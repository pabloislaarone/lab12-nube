# ---------- Etapa 1: compilar React ----------
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

# ---------- Etapa 2: Nginx sirve el frontend y balancea /api ----------
FROM nginx:1.28-alpine
COPY --from=build /app/dist /usr/share/nginx/html
# La configuración del balanceador se monta desde infra/nginx/default.conf (ver docker-compose.yml)
