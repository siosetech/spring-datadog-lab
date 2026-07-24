package tech.sioseforge.auth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import tech.sioseforge.auth.client.UserProfileClient;

@Configuration
public class UserProfileClientConfig {

    @Value("${services.user-profile.url:http://localhost:9082}")
    private String userProfileServiceUrl;

    @Bean
    public UserProfileClient userProfileClient() {
        RestClient restClient = RestClient.builder()
                .baseUrl(userProfileServiceUrl)
                .build();
        RestClientAdapter adapter = RestClientAdapter.create(restClient);
        HttpServiceProxyFactory factory = HttpServiceProxyFactory.builderFor(adapter).build();
        return factory.createClient(UserProfileClient.class);
    }
}
