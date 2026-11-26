package io.matheusms.lastro.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UserTest {

    @Test
    void shouldNormalizeUsernameToLowerCase() {
        assertThat(user(" Matheus.MS ").username()).isEqualTo("matheus.ms");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ab", "with space", "acentuação", "semi;colon"})
    void shouldRejectInvalidUsernames(String username) {
        assertThatThrownBy(() -> user(username)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldHidePasswordHashInCredentialsToString() {
        var credentials = new UserCredentials(user("matheus"), "{bcrypt}secret-hash");

        assertThat(credentials.toString()).doesNotContain("secret-hash");
    }

    private static User user(String username) {
        return new User(null, "Matheus", username, UserRole.ADMIN, true);
    }
}
