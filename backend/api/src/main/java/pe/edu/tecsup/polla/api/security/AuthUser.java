package pe.edu.tecsup.polla.api.security;

/** Usuario autenticado, reconstruido desde el JWT (sin consultar la BD). */
public record AuthUser(long id, String email, String name, String role) {

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}
