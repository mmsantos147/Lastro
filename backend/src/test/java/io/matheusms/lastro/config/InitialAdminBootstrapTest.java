package io.matheusms.lastro.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.matheusms.lastro.domain.model.User;
import io.matheusms.lastro.domain.model.UserRole;
import io.matheusms.lastro.domain.port.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class InitialAdminBootstrapTest {

    private static final String PASSWORD = "a-long-enough-password";

    private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    @Mock
    private UserRepository users;

    @Test
    void shouldCreateAdminWithHashedPasswordWhenThereAreNoUsers() {
        given(users.existsAny()).willReturn(false);
        given(users.create(any(), anyString())).willAnswer(invocation -> invocation.getArgument(0));

        run(new BootstrapAdminProperties("Matheus", PASSWORD, "Matheus S."));

        var user = ArgumentCaptor.forClass(User.class);
        var hash = ArgumentCaptor.forClass(String.class);
        verify(users).create(user.capture(), hash.capture());
        assertThat(user.getValue().username()).isEqualTo("matheus");
        assertThat(user.getValue().name()).isEqualTo("Matheus S.");
        assertThat(user.getValue().role()).isEqualTo(UserRole.ADMIN);
        assertThat(hash.getValue()).isNotEqualTo(PASSWORD).startsWith("{bcrypt}");
        assertThat(passwordEncoder.matches(PASSWORD, hash.getValue())).isTrue();
    }

    @Test
    void shouldNotCreateAdminWhenUsersAlreadyExist() {
        given(users.existsAny()).willReturn(true);

        run(new BootstrapAdminProperties("matheus", PASSWORD, null));

        verify(users, never()).create(any(), anyString());
    }

    @Test
    void shouldNotCreateAdminWhenCredentialsAreNotConfigured() {
        given(users.existsAny()).willReturn(false);

        run(new BootstrapAdminProperties("", "", null));

        verify(users, never()).create(any(), anyString());
    }

    @Test
    void shouldRefuseShortPasswords() {
        given(users.existsAny()).willReturn(false);

        assertThatThrownBy(() -> run(new BootstrapAdminProperties("matheus", "short", null)))
                .isInstanceOf(IllegalStateException.class);
        verify(users, never()).create(any(), anyString());
    }

    private void run(BootstrapAdminProperties properties) {
        new InitialAdminBootstrap(users, passwordEncoder, properties).run(new DefaultApplicationArguments());
    }
}
