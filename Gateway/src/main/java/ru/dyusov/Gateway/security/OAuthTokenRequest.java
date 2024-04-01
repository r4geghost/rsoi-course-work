package ru.dyusov.Gateway.security;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor(staticName = "build")
@NoArgsConstructor
@Data
public class OAuthTokenRequest {
    @JsonProperty("clientId")
    String clientId;
    @JsonProperty("clientSecret")
    String clientSecret;
    @JsonProperty("username")
    String username;
    @JsonProperty("password")
    String password;
}
