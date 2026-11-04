# Marcador 26 · Mundial 2026

## Estructura del proyecto

```
lab12-nube/
├── backend/                  # Maven multi-módulo (Java 21, Spring Boot 3.5)
│   ├── scoring-core/         # Motor de puntuación (5 reglas), Java puro + pruebas unitarias
│   ├── api/                  # API REST: auth JWT, partidos, pronósticos, salas, ranking, admin
│   │   └── src/main/resources/db/migration/   # Esquema y datos (Flyway)
│   ├── worker/               # Consume RabbitMQ y recalcula los puntajes
│   └── Dockerfile            # Imagen multi-etapa (MODULE=api | worker)
├── frontend/                 # React + Vite (se sirve con Nginx)
├── infra/
│   ├── nginx/default.conf    # Balanceador de carga + frontend
│   ├── prometheus/           # Monitoreo (perfil "monitoring")
│   └── grafana/              # Tablero de escalamiento
├── k6/stress.js              # Prueba de estrés
├── scripts/                  # Comparación 1 vs 3 réplicas y evidencia de balanceo (.sh / .ps1)
├── docs/                     # Arquitectura, reglas, pruebas y resultados
├── docker-compose.yml
└── .env.example
```

## Cómo iniciarlo

Requisito: Docker Desktop.

```bash
docker compose up -d --build
```

- Aplicación: http://localhost:8080
- Administrador: `admin@marcador.pe` / `admin123`
- Consola de RabbitMQ: http://localhost:15672 (`marcador` / `marcador`)

Datos de prueba: entra como admin → **Admin → Herramientas → Generar datos demo**. Luego puedes ingresar con `demo01@marcador.pe` / `demo123`.

### Escalar la API

```bash
docker compose up -d --scale api=5
./scripts/balanceo.sh            # Windows: .\scripts\balanceo.ps1
```

### Prueba de estrés (1 vs 3 réplicas)

```bash
./scripts/stress-compare.sh      # Windows: .\scripts\stress-compare.ps1
```

Los resultados se guardan en `docs/resultados/`.

### Monitoreo (opcional)

```bash
docker compose --profile monitoring up -d
```

Grafana: http://localhost:3000

### Pruebas automáticas

```bash
cd backend
mvn verify
```

Las pruebas de integración necesitan Docker encendido (Testcontainers).

### Frontend en modo desarrollo

```bash
cd frontend
npm install
npm run dev
```

Abre http://localhost:5173. Usa la API de Docker en el puerto 8080.
