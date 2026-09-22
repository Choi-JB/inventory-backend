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

    public JwtProvider(
        @Value("${jwt.secret}") String secret,
        @Value("${jwt.expiration}") long expirationMs
    ){
        // 서명: 문자열 비밀키를 HMAC-SHA 서명에 쓸 수 있는 SecretKey 객체로 변환
        this.key = Keys.hmacShaKeyFor(secret.getBytes());   
        this.expirationMs = expirationMs;
    }

    //토큰 생성
    public String generateToken(User user){
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);  // 만료시간: 현재 시간 + 만료시간

        return Jwts.builder()
            .subject(String.valueOf(user.getId()))  //사용자를 식별하는 값을 넣음( ex: 사용자 아이디)
            .claim("email", user.getEmail()) //JWT payload에 포함될 정보
            .claim("role", user.getRole().name()) //JWT payload에 포함될 정보
            .issuedAt(now) //JWT 발급시간
            .expiration(expiry) //JWT 만료시간
            .signWith(key) //JWT 서명
            .compact(); //JWT 토큰 생성
    }
    //토큰 페이로드 추출 : 서명이 유효하지 않으면 예외 발생
    public Claims getClaims(String token){
        return Jwts.parser()
                .verifyWith(key)  // 서명 검증을 위한 키 설정
                .build()
                .parseSignedClaims(token)  // 토큰 페이로드 추출
                .getPayload();  // 토큰 페이로드 반환
    }
    //토큰 검증 : 서명이 유효한지 확인
    public boolean validateToken(String token){
        try {
            getClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

}
