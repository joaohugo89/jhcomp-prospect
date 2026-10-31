# Frontend JH.COMP Prospect — build multi-estagio.
#
# O resultado e um nginx servindo arquivos estaticos; o Node nao vai para a
# imagem final.

# ---------- Estagio 1: build ----------
FROM node:24-alpine AS build

WORKDIR /build

# npm ci exige o lockfile e instala exatamente as versoes fixadas.
COPY package.json package-lock.json ./
RUN npm ci --no-fund --no-audit

COPY tsconfig.json tsconfig.app.json tsconfig.node.json vite.config.ts index.html ./
COPY src ./src

# Caminho relativo: o nginx desta mesma imagem faz o proxy da API, mantendo
# frontend e backend na mesma origem (ver nginx.conf).
ENV VITE_API_BASE_URL=/api/v1

RUN npm run build

# ---------- Estagio 2: runtime ----------
FROM nginx:1.29-alpine AS runtime

COPY --from=build /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY security-headers.conf /etc/nginx/snippets/security-headers.conf

EXPOSE 80

HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://127.0.0.1/ || exit 1
