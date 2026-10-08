# ==============================================================================
# Dockerfile del frontend Angular 22 (login JWT).
#
# Etapa 1: compila la SPA con Node 22.
# Etapa 2: la sirve con nginx (SPA + proxy /api y /auth hacia el gateway).
# ==============================================================================

FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build -- --configuration=production

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/frontend/browser/ /usr/share/nginx/html/
EXPOSE 80