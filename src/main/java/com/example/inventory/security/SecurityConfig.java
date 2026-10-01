/**
 * Spring Security 설정
 * SecurityFilterChain 빈 등록
 * JWT 인증 필터 등록
 * OAuth2 로그인 성공 시 처리
 */
package com.example.inventory.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.cors.CorsConfiguration;
import java.util.List;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import com.example.inventory.exception.ErrorResponse;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    private final ObjectMapper objectMapper;

    public SecurityConfig(
        JwtAuthenticationFilter jwtAuthenticationFilter,
        OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler,
        ObjectMapper objectMapper
    ) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.oAuth2LoginSuccessHandler = oAuth2LoginSuccessHandler;
        this.objectMapper = objectMapper;
    }

    /**
     * SecurityFilterChain 빈 등록
     * JWT 인증 필터 등록
     * OAuth2 로그인 성공 시 처리
     * 예외처리: 인증 실패 시 처리
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource())) // CORS 설정 활성화
            .csrf(csrf -> csrf.disable())   // CSRF 보호 비활성화(세션 방식에서 의미 있음)
            .sessionManagement(session ->   // 세션 관리 설정
                session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .securityContext(securityContext -> securityContext   // 보안 컨텍스트 설정
                .securityContextRepository(new RequestAttributeSecurityContextRepository())
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/me").authenticated() // /api/auth/me 경로는 인증 필요
                .requestMatchers("/api/auth/**").permitAll()   // 인증 없이 접근 가능한 경로
                .anyRequest().authenticated()   // 나머지 요청은 인증 필요
            )
            .oauth2Login(oauth2 -> oauth2
                .redirectionEndpoint(redirection -> redirection
                    .baseUri("/api/auth/google/callback"))
                .successHandler(oAuth2LoginSuccessHandler)
            )
            .exceptionHandling(exception -> exception       //예외처리: 인증 실패 시 처리
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json;charset=UTF-8");
                    ErrorResponse errorResponse = ErrorResponse.of("UNAUTHORIZED", "로그인이 필요합니다.");
                    response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
                })
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);   // JWT 인증 필터를 기본 인증 필터 전에 추가

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        // 여기서 CorsConfiguration 객체를 만들고 설정값(allowedOrigins 등)을 채운 다음
        // UrlBasedCorsConfigurationSource에 등록해서 반환하세요
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("http://localhost:3000"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }


}
