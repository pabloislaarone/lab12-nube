package pe.edu.tecsup.marcador.api.auth;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import pe.edu.tecsup.marcador.api.auth.AuthController.AuthResponse;
import pe.edu.tecsup.marcador.api.auth.AuthController.LoginRequest;
import pe.edu.tecsup.marcador.api.auth.AuthController.RegisterRequest;
import pe.edu.tecsup.marcador.api.auth.AuthController.UserView;
import pe.edu.tecsup.marcador.api.common.ApiException;
import pe.edu.tecsup.marcador.api.security.AuthUser;
import pe.edu.tecsup.marcador.api.security.JwtService;

import java.util.Optional;

@Service
public class AuthService {

    private record UserRow(long id, String name, String email, String passwordHash, String role) {
    }

    private final JdbcClient jdbc;
    private final PasswordEncoder encoder;
    private final JwtService jwtService;

    public AuthService(JdbcClient jdbc, PasswordEncoder encoder, JwtService jwtService) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.jwtService = jwtService;
    }

    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        Optional<Long> id = jdbc.sql("""
                        INSERT INTO users (name, email, password_hash)
                        VALUES (:name, :email, :hash)
                        ON CONFLICT (email) DO NOTHING
                        RETURNING id
                        """)
                .param("name", request.name().trim())
                .param("email", email)
                .param("hash", encoder.encode(request.password()))
                .query(Long.class)
                .optional();
        if (id.isEmpty()) {
            throw ApiException.conflict("Ya existe una cuenta con ese correo");
        }
        AuthUser user = new AuthUser(id.get(), email, request.name().trim(), "USER");
        return new AuthResponse(jwtService.issue(user), UserView.of(user));
    }

    public AuthResponse login(LoginRequest request) {
        UserRow row = jdbc.sql("SELECT id, name, email, password_hash, role FROM users WHERE email = :email")
                .param("email", request.email().trim().toLowerCase())
                .query((rs, i) -> new UserRow(rs.getLong("id"), rs.getString("name"), rs.getString("email"),
                        rs.getString("password_hash"), rs.getString("role")))
                .optional()
                .filter(u -> encoder.matches(request.password(), u.passwordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Correo o contraseña incorrectos"));
        AuthUser user = new AuthUser(row.id(), row.email(), row.name(), row.role());
        return new AuthResponse(jwtService.issue(user), UserView.of(user));
    }
}
