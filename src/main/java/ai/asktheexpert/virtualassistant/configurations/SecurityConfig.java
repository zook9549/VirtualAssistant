package ai.asktheexpert.virtualassistant.configurations;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;


@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    @Order(-1)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeRequests((auth) -> auth.requestMatchers("/pubsub/**").permitAll())
                .securityMatchers((auth) -> auth.requestMatchers("/pubsub/**"));
        http.csrf(AbstractHttpConfigurer::disable);
        return http.build();
    }

    @Bean
    public SecurityFilterChain securityFilterChain2(HttpSecurity http) throws Exception {
        http
                .authorizeRequests()
                .requestMatchers("/auth/**", "/login/**").authenticated()
                .anyRequest().permitAll()
                .and()
                .oauth2Login();
        http.csrf(AbstractHttpConfigurer::disable);
        return http.build();
    }
}