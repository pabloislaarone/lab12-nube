package pe.edu.tecsup.polla.api.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Crea el usuario administrador al iniciar. Es seguro con varias réplicas
 * arrancando a la vez gracias a ON CONFLICT DO NOTHING.
 */
@Component
public class AdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final JdbcClient jdbc;
    private final PasswordEncoder encoder;
    private final AppProperties props;

    public AdminSeeder(JdbcClient jdbc, PasswordEncoder encoder, AppProperties props) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        int created = jdbc.sql("""
                        INSERT INTO users (name, email, password_hash, role)
                        VALUES (:name, :email, :hash, 'ADMIN')
                        ON CONFLICT (email) DO NOTHING
                        """)
                .param("name", props.admin().name())
                .param("email", props.admin().email().toLowerCase())
                .param("hash", encoder.encode(props.admin().password()))
                .update();
        if (created > 0) {
            log.info("Administrador creado: {}", props.admin().email());
        }
    }
}
