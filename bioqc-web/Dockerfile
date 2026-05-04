FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
ARG VITE_API_URL=http://localhost:8080/api
ENV VITE_API_URL=$VITE_API_URL
RUN npm run build

FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY --from=build /app/public/config.js.template /usr/share/nginx/html/config.js.template
COPY --from=build /app/public/robots.txt.template /usr/share/nginx/html/robots.txt.template
COPY --from=build /app/public/sitemap.xml.template /usr/share/nginx/html/sitemap.xml.template
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY docker-entrypoint.d/40-generate-seo.sh /docker-entrypoint.d/40-generate-seo.sh
RUN chmod +x /docker-entrypoint.d/40-generate-seo.sh
EXPOSE 3000
CMD ["nginx", "-g", "daemon off;"]
