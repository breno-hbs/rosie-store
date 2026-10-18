package com.rosie.backend.config;

import com.rosie.backend.model.Role;
import com.rosie.backend.model.Usuario;
import com.rosie.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminInicialTest {

    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Test
    void criaAdminComSenhaEmHashQuandoNaoExisteNenhum() {
        when(usuarioRepository.existsByRole(Role.ADMIN)).thenReturn(false);

        adminInicial(" Roseane@Rosie.TEST ", "senha-forte-123").run(null);

        ArgumentCaptor<Usuario> salvo = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(salvo.capture());
        assertThat(salvo.getValue().getRole()).isEqualTo(Role.ADMIN);
        assertThat(salvo.getValue().getEmail()).isEqualTo("roseane@rosie.test");
        assertThat(salvo.getValue().getSenhaHash()).isNotEqualTo("senha-forte-123");
        assertThat(passwordEncoder.matches("senha-forte-123", salvo.getValue().getSenhaHash())).isTrue();
    }

    @Test
    void naoMexeQuandoJaExisteAdmin() {
        when(usuarioRepository.existsByRole(Role.ADMIN)).thenReturn(true);

        adminInicial("roseane@rosie.test", "senha-forte-123").run(null);

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void semVariaveisNaoCriaNada() {
        when(usuarioRepository.existsByRole(Role.ADMIN)).thenReturn(false);

        adminInicial("", "").run(null);

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void senhaCurtaImpedeASubida() {
        when(usuarioRepository.existsByRole(Role.ADMIN)).thenReturn(false);

        assertThatThrownBy(() -> adminInicial("roseane@rosie.test", "curta").run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ROSIE_ADMIN_SENHA");
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void emailJaUsadoPorClienteNaoViraAdmin() {
        when(usuarioRepository.existsByRole(Role.ADMIN)).thenReturn(false);
        when(usuarioRepository.existsByEmail("roseane@rosie.test")).thenReturn(true);

        adminInicial("roseane@rosie.test", "senha-forte-123").run(null);

        verify(usuarioRepository, never()).save(any());
    }

    private AdminInicial adminInicial(String email, String senha) {
        return new AdminInicial(usuarioRepository, passwordEncoder, "Roseane", email, senha);
    }
}
