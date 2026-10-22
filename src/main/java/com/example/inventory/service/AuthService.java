/**
 * Google OAuth2 로그인 처리
 * UserRepository로 google_id 기준 조회 → 없으면 새로 생성(role 기본값 STAFF)
 * JwtProvider.generateToken(user)로 JWT 발급
 * { accessToken, user } 형태로 JSON 응답 작성
 */
package com.example.inventory.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import com.example.inventory.dto.response.LoginResponse;
import com.example.inventory.dto.request.LoginRequest;
import com.example.inventory.dto.request.RefreshRequest;
import com.example.inventory.exception.NotFoundException;
import com.example.inventory.exception.ConflictException;
import com.example.inventory.exception.ValidationException;
import com.example.inventory.exception.InvalidTokenException;
import com.example.inventory.entity.User;
import com.example.inventory.enums.Role;
import com.example.inventory.repository.RefreshTokenRepository;
import com.example.inventory.repository.UserRepository;
import com.example.inventory.security.JwtProvider;
import com.example.inventory.security.TokenHasher;
import com.example.inventory.entity.RefreshToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.inventory.dto.request.SignUpRequest;
import io.jsonwebtoken.Claims;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProvider jwtProvider;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, RefreshTokenRepository refreshTokenRepository,
            JwtProvider jwtProvider, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtProvider = jwtProvider;
        this.passwordEncoder = passwordEncoder;
    }

    // google 로그인 처리
    public LoginResponse loginWithGoogle(String googleId, String email) {
        User user = userRepository.findByGoogleId(googleId)
                .orElseGet(() -> { // 로그인 시 유저가 없으면 새로 생성
                    User createdUser = userRepository.save(User.google(googleId, email, Role.STAFF));
                    createdUser.assignDefaultUserName();
                    User saved = userRepository.save(createdUser);
                    log.info("신규 사용자 가입: userId={}, name={}, email={}", saved.getId(), saved.getName(), email);
                    return saved;
                });

        String accessToken = jwtProvider.generateToken(user);
        String refreshToken = issueRefreshToken(user);
        log.info("Google 로그인 성공: userId={}, name={}, role={}", user.getId(), user.getName(), user.getRole());

        return new LoginResponse(
                accessToken,
                refreshToken,
                new LoginResponse.UserInfo(user.getId(), user.getEmail(), user.getRole().name()));
    }

    // 일반 로그인 처리
    public LoginResponse login(LoginRequest loginRequest) {
        User user = userRepository.findByEmail(loginRequest.email())
                .orElseThrow(() -> new NotFoundException("존재하지 않는 사용자입니다. 이메일을 확인해주세요."));

        // 구글 로그인 사용자는 비밀번호가 없으므로 예외 처리
        if (user.getPasswordHash() == null) {
            throw new ValidationException("이메일/비밀번호로 가입된 계정이 아닙니다. 구글 로그인을 이용해주세요.");
        }

        // 일반 로그인 사용자는 비밀번호 일치 여부 확인
        if (!passwordEncoder.matches(loginRequest.password(), user.getPasswordHash())) {
            throw new ValidationException("비밀번호가 일치하지 않습니다.");
        }

        String accessToken = jwtProvider.generateToken(user);
        String refreshToken = issueRefreshToken(user);

        log.info("로그인 성공: userId={}, name={}, role={}", user.getId(), user.getName(), user.getRole());

        return new LoginResponse(accessToken, refreshToken,
                new LoginResponse.UserInfo(user.getId(), user.getEmail(), user.getRole().name()));
    }

    // 회원가입 처리
    public void signUp(SignUpRequest signupRequest) {
        if (!signupRequest.password().equals(signupRequest.passwordConfirm())) {
            throw new ValidationException("비밀번호가 일치하지 않습니다.");
        }
        if (userRepository.findByEmail(signupRequest.email()).isPresent()) {
            throw new ConflictException("이미 가입된 이메일입니다.");
        }

        //비밀번호 hash 처리
        String hash = passwordEncoder.encode(signupRequest.password());
        User user = userRepository.save(User.local(signupRequest.email(), hash, Role.STAFF));
        user.assignDefaultUserName();
        userRepository.save(user);
        log.info("회원가입 성공: userId={}, name={}, email={}", user.getId(), user.getName(), user.getEmail());
    }

    /** 로그아웃 시 쿠키의 리프레시 토큰만 폐기한다. 쿠키가 없으면 아무 것도 하지 않는다. */
    @Transactional
    public void revokeRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(TokenHasher.sha256(refreshToken)).ifPresent(stored -> {
            if (stored.getRevokedAt() == null) {
                stored.revoke(LocalDateTime.now());
            }
        });
    }

    // 토큰 갱신 처리
    @Transactional
    public LoginResponse refresh(RefreshRequest refreshRequest) {
        if (!jwtProvider.validateRefreshToken(refreshRequest.refreshToken())) {
            throw new InvalidTokenException("유효하지 않은 리프레시 토큰입니다.");
        }
        String tokenHash = TokenHasher.sha256(refreshRequest.refreshToken());
        RefreshToken stored = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new InvalidTokenException("유효하지 않은 리프레시 토큰입니다."));
        if (stored.getRevokedAt() != null || stored.getExpiresAt() == null
                || stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidTokenException("유효하지 않은 리프레시 토큰입니다.");
        }

        Claims claims = jwtProvider.getClaims(refreshRequest.refreshToken());
        Long userId = Long.valueOf(claims.getSubject());
        if (!stored.getUserId().equals(userId)) {
            throw new InvalidTokenException("유효하지 않은 리프레시 토큰입니다.");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 사용자입니다."));

        // 이미 다른 요청이 폐기했으면 0. 새 토큰을 만들지 않는다.
        int revoked = refreshTokenRepository.revokeIfActive(stored.getId(), LocalDateTime.now());
        if (revoked == 0) {
            throw new InvalidTokenException("유효하지 않은 리프레시 토큰입니다.");
        }

        String accessToken = jwtProvider.generateToken(user);
        String refreshToken = issueRefreshToken(user);

        log.info("토큰 갱싱 성공: userId={}, name={}, role={}", user.getId(), user.getName(), user.getRole());
        return new LoginResponse(accessToken, refreshToken,
                new LoginResponse.UserInfo(user.getId(), user.getEmail(), user.getRole().name()));
    }

    /** 리프레시 토큰을 발급하고, 그 해시와 만료 시각만 DB에 저장한다. */
    private String issueRefreshToken(User user) {
        String refreshToken = jwtProvider.generateRefreshToken(user);
        LocalDateTime expiresAt = LocalDateTime.ofInstant(
                jwtProvider.getClaims(refreshToken).getExpiration().toInstant(),
                ZoneId.systemDefault());
        refreshTokenRepository.save(new RefreshToken(user.getId(), TokenHasher.sha256(refreshToken), expiresAt));
        return refreshToken;
    }
}