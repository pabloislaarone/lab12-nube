-- Partidos de ejemplo (fixture simulado con selecciones del Mundial 2026).
-- Las fechas son relativas al primer arranque para que siempre haya partidos
-- pasados (para cargar resultados) y futuros (para pronosticar).

INSERT INTO matches (stage, home_team, away_team, home_code, away_code, venue, kickoff_at) VALUES
-- Pasados: el admin carga el resultado (o se usa el simulador de demo)
('Grupo A', 'México',        'Sudáfrica',      'MEX', 'RSA', 'Estadio Azteca, CDMX',       date_trunc('hour', now()) - interval '6 days'),
('Grupo B', 'Canadá',        'Catar',          'CAN', 'QAT', 'BMO Field, Toronto',         date_trunc('hour', now()) - interval '5 days 3 hours'),
('Grupo D', 'Estados Unidos','Paraguay',       'USA', 'PAR', 'SoFi Stadium, Los Ángeles',  date_trunc('hour', now()) - interval '5 days'),
('Grupo C', 'Brasil',        'Marruecos',      'BRA', 'MAR', 'MetLife Stadium, NY/NJ',     date_trunc('hour', now()) - interval '4 days'),
('Grupo J', 'Argentina',     'Argelia',        'ARG', 'ALG', 'Arrowhead, Kansas City',     date_trunc('hour', now()) - interval '3 days 4 hours'),
('Grupo H', 'España',        'Cabo Verde',     'ESP', 'CPV', 'Mercedes-Benz, Atlanta',     date_trunc('hour', now()) - interval '3 days'),
('Grupo I', 'Francia',       'Senegal',        'FRA', 'SEN', 'MetLife Stadium, NY/NJ',     date_trunc('hour', now()) - interval '2 days'),
('Grupo L', 'Inglaterra',    'Croacia',        'ENG', 'CRO', 'AT&T Stadium, Dallas',       date_trunc('hour', now()) - interval '1 day'),
-- Futuros: se pueden pronosticar
('Grupo K', 'Portugal',      'Colombia',       'POR', 'COL', 'Hard Rock, Miami',           date_trunc('hour', now()) + interval '3 hours'),
('Grupo E', 'Alemania',      'Ecuador',        'GER', 'ECU', 'Lincoln Financial, Filadelfia', date_trunc('hour', now()) + interval '20 hours'),
('Grupo F', 'Países Bajos',  'Japón',          'NED', 'JPN', 'NRG Stadium, Houston',       date_trunc('hour', now()) + interval '1 day 18 hours'),
('Grupo H', 'Uruguay',       'Arabia Saudita', 'URU', 'KSA', 'Hard Rock, Miami',           date_trunc('hour', now()) + interval '2 days 2 hours'),
('Grupo G', 'Bélgica',       'Egipto',         'BEL', 'EGY', 'Lumen Field, Seattle',       date_trunc('hour', now()) + interval '2 days 21 hours'),
('Grupo A', 'Corea del Sur', 'México',         'KOR', 'MEX', 'Estadio Akron, Guadalajara', date_trunc('hour', now()) + interval '3 days 4 hours'),
('Grupo C', 'Escocia',       'Brasil',         'SCO', 'BRA', 'Hard Rock, Miami',           date_trunc('hour', now()) + interval '4 days'),
('Grupo J', 'Austria',       'Argentina',      'AUT', 'ARG', 'AT&T Stadium, Dallas',       date_trunc('hour', now()) + interval '4 days 20 hours'),
('Grupo E', 'Costa de Marfil','Alemania',      'CIV', 'GER', 'BMO Field, Toronto',         date_trunc('hour', now()) + interval '5 days 3 hours'),
('Grupo I', 'Noruega',       'Francia',        'NOR', 'FRA', 'Gillette Stadium, Boston',   date_trunc('hour', now()) + interval '6 days'),
('Grupo L', 'Panamá',        'Inglaterra',     'PAN', 'ENG', 'MetLife Stadium, NY/NJ',     date_trunc('hour', now()) + interval '6 days 22 hours'),
('Grupo K', 'Uzbekistán',    'Portugal',       'UZB', 'POR', 'NRG Stadium, Houston',       date_trunc('hour', now()) + interval '7 days 5 hours'),
('Grupo D', 'Australia',     'Estados Unidos', 'AUS', 'USA', 'Lumen Field, Seattle',       date_trunc('hour', now()) + interval '8 days'),
('Grupo F', 'Túnez',         'Países Bajos',   'TUN', 'NED', 'Arrowhead, Kansas City',     date_trunc('hour', now()) + interval '9 days'),
('Grupo G', 'Irán',          'Bélgica',        'IRN', 'BEL', 'SoFi Stadium, Los Ángeles',  date_trunc('hour', now()) + interval '10 days'),
('Grupo B', 'Suiza',         'Canadá',         'SUI', 'CAN', 'BC Place, Vancouver',        date_trunc('hour', now()) + interval '11 days');
