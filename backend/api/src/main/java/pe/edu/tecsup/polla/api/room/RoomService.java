package pe.edu.tecsup.polla.api.room;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.tecsup.polla.api.common.ApiException;
import pe.edu.tecsup.polla.api.common.Times;
import pe.edu.tecsup.polla.api.ranking.RankingService;
import pe.edu.tecsup.polla.api.security.AuthUser;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class RoomService {

    public record RoomSummary(long id, String name, String description, String inviteCode,
                              long ownerId, String ownerName, int membersCount, String myRole,
                              Instant createdAt) {
    }

    public record Member(long userId, String name, String role, Instant joinedAt) {
    }

    public record RoomDetail(RoomSummary room, List<Member> members) {
    }

    public record InvitePreview(long id, String name, String description, String ownerName,
                                int membersCount, boolean alreadyMember) {
    }

    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 8;
    private static final int MAX_MEMBERS = 100;

    private static final String SELECT_SUMMARY = """
            SELECT r.id, r.name, r.description, r.invite_code, r.owner_id, o.name AS owner_name, r.created_at,
                   (SELECT count(*) FROM room_members c WHERE c.room_id = r.id) AS members_count,
                   me.role AS my_role
            FROM rooms r
            JOIN users o ON o.id = r.owner_id
            JOIN room_members me ON me.room_id = r.id AND me.user_id = :uid
            """;

    private final JdbcClient jdbc;
    private final RankingService ranking;
    private final SecureRandom random = new SecureRandom();

    public RoomService(JdbcClient jdbc, RankingService ranking) {
        this.jdbc = jdbc;
        this.ranking = ranking;
    }

    public List<RoomSummary> findMine(long userId) {
        return jdbc.sql(SELECT_SUMMARY + " ORDER BY r.created_at DESC")
                .param("uid", userId)
                .query((rs, i) -> new RoomSummary(rs.getLong("id"), rs.getString("name"),
                        rs.getString("description"), rs.getString("invite_code"), rs.getLong("owner_id"),
                        rs.getString("owner_name"), rs.getInt("members_count"), rs.getString("my_role"),
                        Times.instant(rs, "created_at")))
                .list();
    }

    private Optional<RoomSummary> findSummary(long roomId, long userId) {
        return jdbc.sql(SELECT_SUMMARY + " WHERE r.id = :rid")
                .param("uid", userId)
                .param("rid", roomId)
                .query((rs, i) -> new RoomSummary(rs.getLong("id"), rs.getString("name"),
                        rs.getString("description"), rs.getString("invite_code"), rs.getLong("owner_id"),
                        rs.getString("owner_name"), rs.getInt("members_count"), rs.getString("my_role"),
                        Times.instant(rs, "created_at")))
                .optional();
    }

    @Transactional
    public RoomSummary create(AuthUser user, String name, String description) {
        long roomId = insertRoomWithUniqueCode(user.id(), name.trim(), blankToNull(description));
        jdbc.sql("INSERT INTO room_members (room_id, user_id, role) VALUES (:rid, :uid, 'OWNER')")
                .param("rid", roomId)
                .param("uid", user.id())
                .update();
        return findSummary(roomId, user.id()).orElseThrow();
    }

    private long insertRoomWithUniqueCode(long ownerId, String name, String description) {
        // ON CONFLICT evita que un código repetido aborte la transacción: simplemente se reintenta
        for (int attempt = 0; attempt < 5; attempt++) {
            Optional<Long> id = jdbc.sql("""
                            INSERT INTO rooms (name, description, invite_code, owner_id)
                            VALUES (:name, :description, :code, :owner)
                            ON CONFLICT (invite_code) DO NOTHING
                            RETURNING id
                            """)
                    .param("name", name)
                    .param("description", description)
                    .param("code", newCode())
                    .param("owner", ownerId)
                    .query(Long.class)
                    .optional();
            if (id.isPresent()) {
                return id.get();
            }
        }
        throw ApiException.conflict("No se pudo generar un código de invitación, intenta de nuevo");
    }

    public RoomDetail detail(AuthUser user, long roomId) {
        RoomSummary summary = findSummary(roomId, user.id())
                .orElseThrow(() -> ApiException.notFound("La sala no existe o no eres miembro"));
        List<Member> members = jdbc.sql("""
                        SELECT rm.user_id, u.name, rm.role, rm.joined_at
                        FROM room_members rm JOIN users u ON u.id = rm.user_id
                        WHERE rm.room_id = :rid
                        ORDER BY rm.role DESC, rm.joined_at
                        """)
                .param("rid", roomId)
                .query((rs, i) -> new Member(rs.getLong("user_id"), rs.getString("name"), rs.getString("role"),
                        Times.instant(rs, "joined_at")))
                .list();
        return new RoomDetail(summary, members);
    }

    public void requireMember(AuthUser user, long roomId) {
        roleOf(user.id(), roomId).orElseThrow(() -> ApiException.notFound("La sala no existe o no eres miembro"));
    }

    private Optional<String> roleOf(long userId, long roomId) {
        return jdbc.sql("SELECT role FROM room_members WHERE room_id = :rid AND user_id = :uid")
                .param("rid", roomId)
                .param("uid", userId)
                .query(String.class)
                .optional();
    }

    private void requireOwner(AuthUser user, long roomId) {
        String role = roleOf(user.id(), roomId)
                .orElseThrow(() -> ApiException.notFound("La sala no existe o no eres miembro"));
        if (!"OWNER".equals(role)) {
            throw ApiException.forbidden("Solo el dueño de la sala puede hacer esto");
        }
    }

    public RoomSummary update(AuthUser user, long roomId, String name, String description) {
        requireOwner(user, roomId);
        jdbc.sql("UPDATE rooms SET name = :name, description = :description WHERE id = :rid")
                .param("name", name.trim())
                .param("description", blankToNull(description))
                .param("rid", roomId)
                .update();
        return findSummary(roomId, user.id()).orElseThrow();
    }

    public void delete(AuthUser user, long roomId) {
        requireOwner(user, roomId);
        jdbc.sql("DELETE FROM rooms WHERE id = :rid").param("rid", roomId).update();
        ranking.evictRoom(roomId);
    }

    public InvitePreview preview(AuthUser user, String code) {
        return jdbc.sql("""
                        SELECT r.id, r.name, r.description, o.name AS owner_name,
                               (SELECT count(*) FROM room_members c WHERE c.room_id = r.id) AS members_count,
                               EXISTS (SELECT 1 FROM room_members m WHERE m.room_id = r.id AND m.user_id = :uid)
                                   AS already_member
                        FROM rooms r JOIN users o ON o.id = r.owner_id
                        WHERE r.invite_code = :code
                        """)
                .param("uid", user.id())
                .param("code", normalizeCode(code))
                .query((rs, i) -> new InvitePreview(rs.getLong("id"), rs.getString("name"),
                        rs.getString("description"), rs.getString("owner_name"), rs.getInt("members_count"),
                        rs.getBoolean("already_member")))
                .optional()
                .orElseThrow(() -> ApiException.notFound("El código de invitación no es válido"));
    }

    @Transactional
    public RoomSummary join(AuthUser user, String code) {
        Long roomId = jdbc.sql("SELECT id FROM rooms WHERE invite_code = :code")
                .param("code", normalizeCode(code))
                .query(Long.class)
                .optional()
                .orElseThrow(() -> ApiException.notFound("El código de invitación no es válido"));

        int members = jdbc.sql("SELECT count(*) FROM room_members WHERE room_id = :rid")
                .param("rid", roomId)
                .query(Integer.class)
                .single();
        if (members >= MAX_MEMBERS) {
            throw ApiException.conflict("La sala está llena (máximo " + MAX_MEMBERS + " participantes)");
        }

        jdbc.sql("""
                        INSERT INTO room_members (room_id, user_id, role) VALUES (:rid, :uid, 'MEMBER')
                        ON CONFLICT (room_id, user_id) DO NOTHING
                        """)
                .param("rid", roomId)
                .param("uid", user.id())
                .update();
        ranking.evictRoom(roomId);
        return findSummary(roomId, user.id()).orElseThrow();
    }

    public void leave(AuthUser user, long roomId) {
        String role = roleOf(user.id(), roomId)
                .orElseThrow(() -> ApiException.notFound("La sala no existe o no eres miembro"));
        if ("OWNER".equals(role)) {
            throw ApiException.conflict("Eres el dueño: puedes eliminar la sala, pero no abandonarla");
        }
        deleteMember(roomId, user.id());
    }

    public void removeMember(AuthUser user, long roomId, long memberId) {
        requireOwner(user, roomId);
        if (memberId == user.id()) {
            throw ApiException.badRequest("No puedes expulsarte a ti mismo");
        }
        deleteMember(roomId, memberId);
    }

    private void deleteMember(long roomId, long userId) {
        jdbc.sql("DELETE FROM room_members WHERE room_id = :rid AND user_id = :uid")
                .param("rid", roomId)
                .param("uid", userId)
                .update();
        ranking.evictRoom(roomId);
    }

    public RoomSummary regenerateCode(AuthUser user, long roomId) {
        requireOwner(user, roomId);
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                jdbc.sql("UPDATE rooms SET invite_code = :code WHERE id = :rid")
                        .param("code", newCode())
                        .param("rid", roomId)
                        .update();
                return findSummary(roomId, user.id()).orElseThrow();
            } catch (DuplicateKeyException ex) {
                // se reintenta con otro código
            }
        }
        throw ApiException.conflict("No se pudo generar un código nuevo, intenta de nuevo");
    }

    private String newCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
        }
        return sb.toString();
    }

    private static String normalizeCode(String code) {
        return code == null ? "" : code.trim().toUpperCase();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
