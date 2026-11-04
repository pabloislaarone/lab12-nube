package pe.edu.tecsup.polla.api.room;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.tecsup.polla.api.ranking.RankingRow;
import pe.edu.tecsup.polla.api.ranking.RankingService;
import pe.edu.tecsup.polla.api.room.RoomService.InvitePreview;
import pe.edu.tecsup.polla.api.room.RoomService.RoomDetail;
import pe.edu.tecsup.polla.api.room.RoomService.RoomSummary;
import pe.edu.tecsup.polla.api.security.AuthUser;

import java.util.List;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    public record RoomRequest(
            @NotBlank @Size(min = 3, max = 60) String name,
            @Size(max = 200) String description) {
    }

    public record JoinRequest(@NotBlank @Size(max = 12) String code) {
    }

    private final RoomService rooms;
    private final RankingService ranking;

    public RoomController(RoomService rooms, RankingService ranking) {
        this.rooms = rooms;
        this.ranking = ranking;
    }

    @GetMapping
    public List<RoomSummary> mine(@AuthenticationPrincipal AuthUser user) {
        return rooms.findMine(user.id());
    }

    @PostMapping
    public ResponseEntity<RoomSummary> create(@AuthenticationPrincipal AuthUser user,
                                              @Valid @RequestBody RoomRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(rooms.create(user, request.name(), request.description()));
    }

    @GetMapping("/{id}")
    public RoomDetail detail(@AuthenticationPrincipal AuthUser user, @PathVariable long id) {
        return rooms.detail(user, id);
    }

    @PatchMapping("/{id}")
    public RoomSummary update(@AuthenticationPrincipal AuthUser user, @PathVariable long id,
                              @Valid @RequestBody RoomRequest request) {
        return rooms.update(user, id, request.name(), request.description());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser user, @PathVariable long id) {
        rooms.delete(user, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/ranking")
    public List<RankingRow> ranking(@AuthenticationPrincipal AuthUser user, @PathVariable long id) {
        rooms.requireMember(user, id);
        return ranking.room(id);
    }

    @GetMapping("/invite/{code}")
    public InvitePreview preview(@AuthenticationPrincipal AuthUser user, @PathVariable String code) {
        return rooms.preview(user, code);
    }

    @PostMapping("/join")
    public RoomSummary join(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody JoinRequest request) {
        return rooms.join(user, request.code());
    }

    @PostMapping("/{id}/leave")
    public ResponseEntity<Void> leave(@AuthenticationPrincipal AuthUser user, @PathVariable long id) {
        rooms.leave(user, id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ResponseEntity<Void> removeMember(@AuthenticationPrincipal AuthUser user, @PathVariable long id,
                                             @PathVariable long userId) {
        rooms.removeMember(user, id, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/invite-code")
    public RoomSummary regenerateCode(@AuthenticationPrincipal AuthUser user, @PathVariable long id) {
        return rooms.regenerateCode(user, id);
    }
}
