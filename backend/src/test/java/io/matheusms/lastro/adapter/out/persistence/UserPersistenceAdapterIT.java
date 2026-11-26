package io.matheusms.lastro.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.matheusms.lastro.domain.model.User;
import io.matheusms.lastro.domain.model.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

@PersistenceAdapterTest
class UserPersistenceAdapterIT {

    @Autowired
    private UserPersistenceAdapter users;

    @Test
    void shouldAssignIdAndFindCreatedUser() {
        var created = users.create(TestData.user(), TestData.PASSWORD_HASH);

        assertThat(created.id()).isNotNull();
        assertThat(users.findById(created.id())).contains(created);
        assertThat(users.existsAny()).isTrue();
    }

    @Test
    void shouldFindCredentialsByUsernameIgnoringCase() {
        var created = users.create(new User(null, "Matheus", "matheus", UserRole.ADMIN, true), "{bcrypt}hash");

        var credentials = users.findCredentialsByUsername(" MATHEUS ").orElseThrow();

        assertThat(credentials.user()).isEqualTo(created);
        assertThat(credentials.passwordHash()).isEqualTo("{bcrypt}hash");
    }

    @Test
    void shouldReportNoUsersWhenEmpty() {
        assertThat(users.existsAny()).isFalse();
        assertThat(users.findCredentialsByUsername("nobody")).isEmpty();
    }

    @Test
    void shouldRejectDuplicateUsername() {
        users.create(new User(null, "A", "matheus", UserRole.ADMIN, true), TestData.PASSWORD_HASH);

        assertThatThrownBy(() -> users.create(
                new User(null, "B", "Matheus", UserRole.USER, true), TestData.PASSWORD_HASH))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
