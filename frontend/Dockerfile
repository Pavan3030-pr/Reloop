# ---------- build ----------
FROM node:20-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build

# ---------- serve ----------
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
# 127.0.0.1, not localhost: nginx binds IPv4 only, and localhost can resolve to ::1 first.
HEALTHCHECK --interval=10s --timeout=3s --start-period=10s --retries=6 \
    CMD wget -qO- http://127.0.0.1/ >/dev/null || exit 1
