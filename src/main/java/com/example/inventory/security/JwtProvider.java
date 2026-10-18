/**
 * JWT 토큰 생성, 검증, 페이로드 추출
 */
package com.example.inventory.security;

import com.example.inventory.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtProvider {
    //키
    private final SecretKey key;
    //만료시간
    private final long expirationMs;
    private final long refreshExpirationMs;

    public JwtProvider(
        @Value("${jwt.secret}") String secret,
        @Value("${jwt.expiration}") long expirationMs,
        @Value("${jwt.refreshExpirationMs}") long refreshExpirationMs
    ){
        // 서명: 문자열 비밀키를 HMAC-SHA 서명에 쓸 수 있는 SecretKey 객체로 변환
        this.key = Keys.hmacShaKeyFor(secret.getBytes());   
        this.expirationMs = expirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    //accessToken 생성
    public String generateToken(User user){
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);  // 만료시간: 현재 시간 + 만료시간

        return Jwts.builder()
            .subject(String.valueOf(user.getId()))  //사용자를 식별하는 값을 넣음( ex: 사용자 아이디)
            .claim("email", user.getEmail()) //JWT payload에 포함될 정보
            .claim("role", user.getRole().name()) //JWT payload에 포함될 정보
            .claim("type", "access") //JWT payload에 포함될 정보
            .issuedAt(now) //JWT 발급시간
            .expiration(expiry) //JWT 만료시간
            .signWith(key) //JWT 서명
            .compact(); //JWT 토큰 생성
    }

    //refreshToken 생성
    public String generateRefreshToken(User user){
        Date now = new Date();
        Date expiry = new Date(now.getTime() + refreshExpirationMs);  // 만료시간: 현재 시간 + 만료시간

        return Jwts.builder()
            .subject(String.valueOf(user.getId()))  //사용자를 식별하는 값을 넣음( ex: 사용자 아이디)
            .claim("type", "refresh") //JWT payload에 포함될 정보
            .issuedAt(now) //JWT 발급시간
            .expiration(expiry) //JWT 만료시간
            .signWith(key) //JWT 서명
            .compact(); //JWT 토큰 생성
    }

    //토큰 페이로드 추출 : 서명이 유효하지 않으면 예외 발생, accessToken 인지 refreshToken 인지 확인
    public Claims getClaims(String token){
        Claims claims = Jwts.parser()
                .verifyWith(key)  // 서명 검증을 위한 키 설정
                .build()
                .parseSignedClaims(token)  // 토큰 페이로드 추출
                .getPayload();  // 토큰 페이로드 반환
        return claims;
    }

    //accessToken 검증 : 서명이 유효한지 확인
    public boolean validateAccessToken(String token){
        try {
            Claims claims = getClaims(token);
            if (claims.get("type").equals("access")) {
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    //refreshToken 검증 : 서명이 유효한지 확인
    public boolean validateRefreshToken(String token){
        try {
            Claims claims = getClaims(token);
            if (claims.get("type").equals("refresh")) {
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

}
