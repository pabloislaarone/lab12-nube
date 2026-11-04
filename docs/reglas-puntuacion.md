# Sistema de puntuación

El motor está en `backend/scoring-core/.../ScoringEngine.java`. Es Java puro, sin Spring, para poder probarlo de forma aislada y reutilizarlo en la API y en el worker.

## Reglas

| # | Regla | Puntos | Cómo se implementó |
|---|---|---|---|
| 1 | Resultado exacto | 5 | El marcador pronosticado es igual al real |
| 2 | Ganador correcto | 3 | El signo de (local − visitante) coincide; el empate también cuenta |
| 3 | Diferencia de goles correcta | 2 | (local − visitante) pronosticado = real |
| 4 | Bonus por racha | +2 | Cada 3 aciertos seguidos (al menos el ganador), en el 3.º, 6.º, 9.º… |
| 5 | Predicción anticipada | +1 | Registrada con **más** de 24 h de anticipación |

## Interpretaciones acordadas

El enunciado deja algunos puntos abiertos. Estas son las decisiones que tomamos, y todas se pueden cambiar con variables de entorno:

1. **Reglas 1, 2 y 3: solo vale la más alta** (`SCORING_COMBINE_MODE=HIGHEST`). Un exacto da 5 y no 5 + 3 + 2.
   - *Consecuencia:* acertar la diferencia siempre implica acertar el ganador, así que en este modo la regla 3 se detecta y se muestra, pero nunca supera a la regla 2.
   - Con `SCORING_COMBINE_MODE=CUMULATIVE`, ganador y diferencia se suman (3 + 2 = 5) y el exacto sigue valiendo 5. Por ejemplo, 3-1 contra 2-0 da 3 en un modo y 5 en el otro.
2. **Racha:** se evalúa en orden cronológico por fecha del partido. **Un partido sin pronóstico corta la racha**, igual que un fallo.
3. **Último minuto** (menos de 10 min antes): recibe solo los puntos base. Tampoco avanza ni corta la racha.
4. **Anticipación:** se toma la hora del **último cambio** del pronóstico. Exactamente 24 h no cuenta, tienen que ser más. Por defecto el +1 solo se da si el pronóstico sumó puntos (`SCORING_EARLY_REQUIRES_HIT=true`).
5. **Cierre:** no se puede pronosticar desde la hora de inicio del partido. Se valida en la API y otra vez en el SQL.
6. **Desempate en el ranking:** puntos → marcadores exactos → aciertos de ganador → nombre.

## Ejemplo completo (caso de la prueba de integración del worker)

| Partido | Real | Pronóstico de Ana | Registrado | Base | Anticip. | Racha | Total |
|---|---|---|---|---|---|---|---|
| 1 | 1-0 | 1-0 | 48 h antes | 5 (exacto) | +1 | — | 6 |
| 2 | 2-2 | 1-1 | 2 h antes | 3 (empate) | — | — | 3 |
| 3 | 3-1 | 3-1 | 2 h antes | 5 (exacto) | — | +2 (3 seguidos) | 7 |
| | | | | | | **Total** | **16** |

Beto, en cambio, pronosticó solo el partido 1 (2-0, 5 min antes). Acierta el ganador, pero como es de último minuto recibe solo los 3 puntos base. No pronosticó los partidos 2 y 3, así que su total es **3**.
