package com.example.usermanagement;

import com.example.usermanagement.config.RootConfig;
import com.example.usermanagement.dto.UserCreateRequest;
import com.example.usermanagement.dto.UserResponse;
import com.example.usermanagement.dto.UserUpdateRequest;
import com.example.usermanagement.entity.Role;
import com.example.usermanagement.exception.EmailAlreadyExistsException;
import com.example.usermanagement.exception.ResourceNotFoundException;
import com.example.usermanagement.repository.UserRepository;
import com.example.usermanagement.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = RootConfig.class)
@DisplayName("CRUD User — Test d'intégration complet")
class UserCrudIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void cleanDb() {
        userRepository.deleteAll();
    }

    // ================================================================
    // CREATE
    // ================================================================

    @Test
    @DisplayName("CREATE — crée un utilisateur et retourne ses données (sans mot de passe)")
    void create_shouldPersistUser() {
        UserCreateRequest req = buildCreateRequest("Alice", "alice@example.com", "secret123", Role.USER);

        UserResponse created = userService.create(req);

        assertNotNull(created.getId());
        assertEquals("Alice", created.getName());
        assertEquals("alice@example.com", created.getEmail());
        assertEquals(Role.USER, created.getRole());
        assertTrue(created.isActive());
        assertNotNull(created.getCreatedAt());
        assertNotNull(created.getUpdatedAt());
    }

    @Test
    @DisplayName("CREATE — refuse un email déjà utilisé (409)")
    void create_shouldRejectDuplicateEmail() {
        userService.create(buildCreateRequest("Alice", "alice@example.com", "secret123", Role.USER));

        UserCreateRequest duplicate =
                buildCreateRequest("Bob", "alice@example.com", "secret456", Role.USER);

        assertThrows(EmailAlreadyExistsException.class, () -> userService.create(duplicate));
    }

    @Test
    @DisplayName("CREATE — le mot de passe est haché en base")
    void create_shouldHashPassword() {
        userService.create(buildCreateRequest("Alice", "alice@example.com", "secret123", Role.USER));

        String stored = userRepository.findByEmailAndActiveTrue("alice@example.com")
                .orElseThrow()
                .getPassword();

        assertNotEquals("secret123", stored);
        assertTrue(stored.startsWith("$2")); // BCrypt
    }

    // ================================================================
    // READ
    // ================================================================

    @Test
    @DisplayName("READ — findById retourne l'utilisateur créé")
    void read_findById_shouldReturnUser() {
        UserResponse created =
                userService.create(buildCreateRequest("Alice", "alice@example.com", "secret123", Role.USER));

        UserResponse found = userService.findById(created.getId());

        assertEquals(created.getId(), found.getId());
        assertEquals("alice@example.com", found.getEmail());
    }

    @Test
    @DisplayName("READ — findById lève 404 si l'utilisateur n'existe pas")
    void read_findById_shouldThrowWhenNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> userService.findById(999L));
    }

    @Test
    @DisplayName("READ — findAllActive ne retourne que les utilisateurs actifs")
    void read_findAllActive_shouldExcludeInactive() {
        UserResponse alice =
                userService.create(buildCreateRequest("Alice", "alice@example.com", "secret123", Role.USER));
        UserResponse bob =
                userService.create(buildCreateRequest("Bob", "bob@example.com", "secret456", Role.USER));

        userService.deactivate(bob.getId());

        List<UserResponse> actives = userService.findAllActive();

        assertEquals(1, actives.size());
        assertEquals(alice.getId(), actives.get(0).getId());
    }

    // ================================================================
    // UPDATE
    // ================================================================

    @Test
    @DisplayName("UPDATE — modifie le nom et l'email")
    void update_shouldModifyUser() {
        UserResponse created =
                userService.create(buildCreateRequest("Alice", "alice@example.com", "secret123", Role.USER));

        UserUpdateRequest update = new UserUpdateRequest();
        update.setName("Alice Updated");
        update.setEmail("alice.new@example.com");

        UserResponse updated = userService.update(created.getId(), update);

        assertEquals("Alice Updated", updated.getName());
        assertEquals("alice.new@example.com", updated.getEmail());
    }

    @Test
    @DisplayName("UPDATE — refuse un email déjà pris par un autre utilisateur")
    void update_shouldRejectEmailTakenByOther() {
        userService.create(buildCreateRequest("Alice", "alice@example.com", "secret123", Role.USER));
        UserResponse bob =
                userService.create(buildCreateRequest("Bob", "bob@example.com", "secret456", Role.USER));

        UserUpdateRequest update = new UserUpdateRequest();
        update.setName("Bob");
        update.setEmail("alice@example.com");

        assertThrows(EmailAlreadyExistsException.class,
                () -> userService.update(bob.getId(), update));
    }

    @Test
    @DisplayName("UPDATE — l'updatedAt est rafraîchi après modification")
    void update_shouldRefreshUpdatedAt() throws InterruptedException {
        UserResponse created =
                userService.create(buildCreateRequest("Alice", "alice@example.com", "secret123", Role.USER));

        Thread.sleep(20);

        UserUpdateRequest update = new UserUpdateRequest();
        update.setName("Alice 2");
        update.setEmail("alice2@example.com");

        UserResponse updated = userService.update(created.getId(), update);

        assertTrue(updated.getUpdatedAt().isAfter(updated.getCreatedAt()));
    }

    // ================================================================
    // DELETE (désactivation logique)
    // ================================================================

    @Test
    @DisplayName("DELETE — désactivation logique (active = false, ligne conservée)")
    void delete_shouldDeactivateUserLogically() {
        UserResponse created =
                userService.create(buildCreateRequest("Alice", "alice@example.com", "secret123", Role.USER));

        userService.deactivate(created.getId());

        assertTrue(userRepository.findById(created.getId()).isPresent());
        assertTrue(userRepository.findByIdAndActiveTrue(created.getId()).isEmpty());
        assertTrue(userService.findAllActive().isEmpty());
    }

    @Test
    @DisplayName("DELETE — un utilisateur désactivé n'est plus accessible via findById")
    void delete_shouldMakeUserUnavailable() {
        UserResponse created =
                userService.create(buildCreateRequest("Alice", "alice@example.com", "secret123", Role.USER));

        userService.deactivate(created.getId());

        assertThrows(ResourceNotFoundException.class,
                () -> userService.findById(created.getId()));
    }

    // ================================================================
    // SCÉNARIO COMPLET
    // ================================================================

    @Test
    @DisplayName("SCÉNARIO — CREATE → READ → UPDATE → DELETE en une seule fois")
    void fullCrudScenario() {
        // CREATE
        UserResponse created =
                userService.create(buildCreateRequest("Alice", "alice@example.com", "secret123", Role.USER));
        assertNotNull(created.getId());

        // READ
        UserResponse read = userService.findById(created.getId());
        assertEquals("Alice", read.getName());

        // UPDATE
        UserUpdateRequest update = new UserUpdateRequest();
        update.setName("Alice Cooper");
        update.setEmail("alice.cooper@example.com");
        UserResponse updated = userService.update(created.getId(), update);
        assertEquals("Alice Cooper", updated.getName());

        // DELETE (soft)
        userService.deactivate(created.getId());
        assertTrue(userService.findAllActive().isEmpty());
        assertThrows(ResourceNotFoundException.class,
                () -> userService.findById(created.getId()));
    }

    // ================================================================
    // HELPER
    // ================================================================

    private UserCreateRequest buildCreateRequest(String name, String email, String password, Role role) {
        UserCreateRequest req = new UserCreateRequest();
        req.setName(name);
        req.setEmail(email);
        req.setPassword(password);
        req.setRole(role);
        return req;
    }
}