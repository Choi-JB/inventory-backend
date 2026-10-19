package com.example.inventory.service;

import com.example.inventory.dto.request.LoginRequest;
import com.example.inventory.dto.request.RefreshRequest;
import com.example.inventory.dto.request.SignUpRequest;
import com.example.inventory.dto.response.LoginResponse;
import com.example.inventory.entity.User;
import com.example.inventory.enums.Role;
import com.example.inventory.exception.ConflictException;
import com.example.inventory.exception.InvalidTokenException;
import com.example.inventory.exception.NotFoundException;
import com.example.inventory.exception.ValidationException;
import com.example.inventory.repository.UserRepository;
import com.example.inventory.security.JwtProvider;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    @Test
    void signUp_정상가입하면_비밀번호를암호화해저장한다() {
        SignUpRequest request = new SignUpRequest("user@test.com", "pass1234", "pass1234");
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("pass1234")).thenReturn("hashed");
        stubSaveAssignsId();

        authService.signUp(request);

        verify(passwordEncoder).encode("pass1234");
        verify(userRepository, times(2)).save(any(User.class));
    }

    @Test
    void signUp_비밀번호확인이다르면_ValidationException() {
        SignUpRequest request = new SignUpRequest("user@test.com", "pass1234", "other");

        assertThatThrownBy(() -> authService.signUp(request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("비밀번호가 일치하지 않습니다");

        verify(userRepository, never()).save(any());
    }

    @Test
    void signUp_이미가입된이메일이면_ConflictException() {
        SignUpRequest request = new SignUpRequest("user@test.com", "pass1234", "pass1234");
        when(userRepository.findByEmail("user@test.com"))
                .thenReturn(Optional.of(localUser(1L, "user@test.com", "hashed")));

        assertThatThrownBy(() -> authService.signUp(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("이미 가입된 이메일");

        verify(userRepository, never()).save(any());
    }

    @Test
    void login_정상로그인이면_토큰과유저정보를반환한다() {
        User user = localUser(1L, "user@test.com", "hashed");
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pass1234", "hashed")).thenReturn(true);
        when(jwtProvider.generateToken(user)).thenReturn("access");
        when(jwtProvider.generateRefreshToken(user)).thenReturn("refresh");

        LoginResponse response = authService.login(new LoginRequest("user@test.com", "pass1234"));

        assertThat(response.accessToken()).isEqualTo("access");
        assertThat(response.refreshToken()).isEqualTo("refresh");
        assertThat(response.user().id()).isEqualTo(1L);
        assertThat(response.user().email()).isEqualTo("user@test.com");
        assertThat(response.user().role()).isEqualTo("STAFF");
    }

    @Test
    void login_없는이메일이면_NotFoundException() {
        when(userRepository.findByEmail("none@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("none@test.com", "pass")))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("존재하지 않는 사용자");
    }

    @Test
    void login_구글전용계정이면_ValidationException() {
        User googleUser = googleUser(1L, "google-sub", "user@test.com");
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(googleUser));

        assertThatThrownBy(() -> authService.login(new LoginRequest("user@test.com", "pass")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("구글 로그인");
    }

    @Test
    void login_비밀번호불일치면_ValidationException() {
        User user = localUser(1L, "user@test.com", "hashed");
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("user@test.com", "wrong")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("비밀번호가 일치하지 않습니다");
    }

    @Test
    void loginWithGoogle_기존유저면_저장없이토큰을발급한다() {
        User user = googleUser(1L, "google-sub", "user@test.com");
        when(userRepository.findByGoogleId("google-sub")).thenReturn(Optional.of(user));
        when(jwtProvider.generateToken(user)).thenReturn("access");
        when(jwtProvider.generateRefreshToken(user)).thenReturn("refresh");

        LoginResponse response = authService.loginWithGoogle("google-sub", "user@test.com");

        assertThat(response.accessToken()).isEqualTo("access");
        assertThat(response.refreshToken()).isEqualTo("refresh");
        assertThat(response.user().id()).isEqualTo(1L);
        verify(userRepository, never()).save(any());
    }

    @Test
    void loginWithGoogle_신규유저면_저장후토큰을발급한다() {
        when(userRepository.findByGoogleId("new-sub")).thenReturn(Optional.empty());
        stubSaveAssignsId();
        when(jwtProvider.generateToken(any(User.class))).thenReturn("access");
        when(jwtProvider.generateRefreshToken(any(User.class))).thenReturn("refresh");

        LoginResponse response = authService.loginWithGoogle("new-sub", "new@test.com");

        assertThat(response.accessToken()).isEqualTo("access");
        assertThat(response.refreshToken()).isEqualTo("refresh");
        assertThat(response.user().email()).isEqualTo("new@test.com");
        verify(userRepository, times(2)).save(any(User.class));
    }

    @Test
    void refresh_유효한토큰이면_새토큰을발급한다() {
        User user = localUser(1L, "user@test.com", "hashed");
        Claims claims = mock(Claims.class);
        when(jwtProvider.validateRefreshToken("refresh-token")).thenReturn(true);
        when(jwtProvider.getClaims("refresh-token")).thenReturn(claims);
        when(claims.getSubject()).thenReturn("1");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(jwtProvider.generateToken(user)).thenReturn("new-access");
        when(jwtProvider.generateRefreshToken(user)).thenReturn("new-refresh");

        LoginResponse response = authService.refresh(new RefreshRequest("refresh-token"));

        assertThat(response.accessToken()).isEqualTo("new-access");
        assertThat(response.refreshToken()).isEqualTo("new-refresh");
        assertThat(response.user().id()).isEqualTo(1L);
    }

    @Test
    void refresh_유효하지않은토큰이면_InvalidTokenException() {
        when(jwtProvider.validateRefreshToken("bad-token")).thenReturn(false);

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("bad-token")))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("유효하지 않은 리프레시 토큰");

        verify(jwtProvider, never()).getClaims(anyString());
    }

    @Test
    void refresh_토큰의유저가없으면_NotFoundException() {
        Claims claims = mock(Claims.class);
        when(jwtProvider.validateRefreshToken("refresh-token")).thenReturn(true);
        when(jwtProvider.getClaims("refresh-token")).thenReturn(claims);
        when(claims.getSubject()).thenReturn("99");
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("refresh-token")))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("존재하지 않는 사용자");
    }

    private User localUser(Long id, String email, String passwordHash) {
        User user = User.local(email, passwordHash, Role.STAFF);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private User googleUser(Long id, String googleId, String email) {
        User user = User.google(googleId, email, Role.STAFF);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private void stubSaveAssignsId() {
        AtomicLong seq = new AtomicLong(1);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User user = inv.getArgument(0);
            if (ReflectionTestUtils.getField(user, "id") == null) {
                ReflectionTestUtils.setField(user, "id", seq.getAndIncrement());
            }
            return user;
        });
    }
}
