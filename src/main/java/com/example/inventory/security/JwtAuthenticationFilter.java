/**
 * JWT 토큰 인증 필터
 * 매 요청마다 Authorization: Bearer {JWT} 헤더를 읽어 검증 후 
 * SecurityContext에 인증 정보 세팅 — OncePerRequestFilter 상속
 * SecurityContext 에 등록된 Authentication 객체는 주로 사용자의 접근 권한을 확인
 */
package com.example.inventory.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {//요청당 딱 한 번만 실행되는 필터
    private final JwtProvider jwtProvider;

    public JwtAuthenticationFilter(JwtProvider jwtProvider){
        this.jwtProvider = jwtProvider;
    }

    //토큰 추출
    //Authorization: Bearer {JWT} 헤더에서 JWT 추출
    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    //토큰 검증 및 인증 정보 세팅
    @Override
    protected void doFilterInternal(
        HttpServletRequest request, 
        HttpServletResponse response, 
        FilterChain filterChain
    ) throws ServletException, IOException {
        String token = resolveToken(request);

        //검증 통과한 경우에만 SecurityContextHolder에 인증 객체를 넣음
        if (token != null && jwtProvider.validateToken(token)) {
            Claims claims = jwtProvider.getClaims(token);

            String userId = claims.getSubject();
            String role = claims.get("role", String.class);

            //권한 설정
            List<SimpleGrantedAuthority> authorities =
                    List.of(new SimpleGrantedAuthority("ROLE_" + role));

            //인증 객체 생성
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, null, authorities);

            //인증 객체를 SecurityContextHolder에 넣음
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        //다음 필터 실행
        filterChain.doFilter(request, response);
    }
    

}
