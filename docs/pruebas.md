# Pruebas realizadas

Todas las pruebas corren automáticamente en GitHub Actions (`.github/workflows/ci.yml`) con cada push a `main`.

## 1. Pruebas unitarias del motor de puntuación (26 pruebas, todas pasan)

`backend/scoring-core/src/test/.../ScoringEngineTest.java`

| Grupo | Pruebas | Qué verifica |
|---|---|---|
| Reglas 1, 2 y 3 | 11 | Exacto (5), ganador y empate (3), diferencia detectada, fallos, sin pronóstico |
| Modo acumulativo | 3 | Ganador + diferencia = 5; el exacto sigue en 5 |
| Regla 4 (racha) | 5 | 3 aciertos = +2, 6 aciertos = +4, un fallo o no pronosticar la cortan, orden cronológico |
| Regla 5 (anticipación) | 7 | Más de 24 h = +1, exactamente 24 h no cuenta, último minuto solo base, último minuto no avanza la racha, todas las reglas juntas |
| Idempotencia | 1 | Misma entrada → mismo resultado |

## 1.1 Pruebas unitarias del análisis de estadísticas (6 pruebas)

`InsightEngineTest`: cálculo de indicadores, análisis automático, caso sin resultados, lectura de la respuesta de la IA (incluso con texto alrededor) y descarte de respuestas inválidas.

## 2. Pruebas de integración (19 pruebas, todas pasan)

Usan **Testcontainers**, es decir, Postgres 16, Redis 7 y RabbitMQ 3.13 reales dentro de contenedores.

**API, `ApiFlowTest` (16 pruebas):**
- Registro, login, correo duplicado (409) y contraseña incorrecta (401).
- Pronóstico en un partido futuro, edición sin duplicar y bloqueo de un partido ya iniciado (409).
- Creación de una sala, vista previa con el código, unión con el código en minúsculas y ranking de la sala.
- Solo el dueño regenera el código (403 para un miembro), el dueño no puede abandonar su sala, un no miembro no ve la sala (404) y un miembro abandona.
- Solo el admin carga resultados (403 para un usuario). Al cargar uno, se publica el evento en RabbitMQ.
- No se puede cargar el resultado de un partido futuro, y el ranking excluye al admin.
- Cabecera `X-Instance-Id` en las respuestas.
- El endpoint de estadísticas con IA responde con el análisis; el admin recibe 400.

**Worker, `ScoringWorkerTest` (3 pruebas):**
- Cálculo con las 5 reglas sobre datos reales: Ana = 16 puntos y Beto = 3 (detalle en [reglas-puntuacion.md](reglas-puntuacion.md)).
- Recalcular dos veces da el mismo resultado, sin filas duplicadas.
- Consume el evento desde RabbitMQ y actualiza los puntajes.

## 3. Balanceo de carga

Se hicieron 30 peticiones a `/api/info` a través de Nginx con 3 réplicas (`scripts/balanceo.sh`):

```
Réplica 376585aae1e1   atendió 10 peticiones
Réplica 710e4f39d27d   atendió 10 peticiones
Réplica fd86ae57f3ce   atendió 10 peticiones
```

Los eventos de RabbitMQ también se repartieron entre las **dos réplicas del worker** (`worker-1` y `worker-2` procesaron eventos distintos, ver `resultados/worker.txt`).

## 4. Pruebas de estrés con k6

**Herramienta:** k6 0.54 (`k6/stress.js`). Cada usuario virtual inicia sesión y mezcla las acciones reales de la app:

| Acción | Proporción |
|---|---|
| Ver partidos | 35 % |
| Ranking global | 25 % |
| Pronosticar | 20 % |
| Ranking de la sala | 13 % |
| Estadísticas | 7 % |

**Entorno:** GitHub Actions (4 vCPU). Cada réplica de la API está limitada a **1 CPU y 512 MB**. Se ejecutó `scripts/stress-compare.sh`.

### Escenario A: usuarios realistas (hasta 300 usuarios simultáneos, con pausas de lectura)

| Métrica | 1 réplica | 3 réplicas |
|---|---|---|
| Peticiones totales | 49 720 | 49 507 |
| Peticiones por segundo | 242 | 243 |
| Latencia p95 | 3,6 ms | **1,9 ms** |
| Latencia p99 | 6,4 ms | **2,9 ms** |
| Latencia máxima | 702 ms | **310 ms** |
| Errores | 0 % | 0 % |

### Escenario B: saturación (tasa creciente hasta 1800 iteraciones/s, sin pausas)

| Métrica | 1 réplica | 3 réplicas |
|---|---|---|
| Peticiones totales | 175 625 | 176 286 |
| Peticiones por segundo | 961 | 964 |
| Latencia promedio | 15,5 ms | **1,6 ms** |
| Latencia p95 | 13,6 ms | **3,6 ms** |
| Latencia p99 | 474,7 ms | **9,3 ms** |
| Latencia máxima | 2 672 ms | **207 ms** |
| Usuarios virtuales que necesitó k6 | 653 | 300 |
| Iteraciones descartadas | 660 | **0** |
| Errores | 0 % | 0 % |

### Análisis

- **Con usuarios realistas, una sola réplica ya alcanza** (p95 de 3,6 ms). Aun así, con tres réplicas la latencia baja a la mitad y los picos se reducen.
- **En saturación, una réplica llega a su límite:**
  - La p99 sube a casi medio segundo y hay picos de 2,7 s.
  - Las peticiones se acumulan, así que k6 tuvo que abrir el doble de usuarios virtuales y aun así descartó 660 iteraciones.
- **Con tres réplicas la misma carga se reparte:** la p99 baja de 475 ms a 9 ms (unas **50 veces menos**) y no se descarta ninguna petición.
- **No hubo errores en ningún escenario.** Esto se debe a que:
  - el cálculo de puntos va por RabbitMQ y no bloquea la API;
  - el ranking se sirve desde la caché de Redis.
- **Conclusión:** el escalamiento horizontal funciona porque la API no tiene estado. El siguiente cuello de botella sería la base de datos compartida, que se podría escalar con réplicas de lectura.

Los resultados completos están en `docs/resultados/`.
