package ru.dyusov.Gateway.security;

import org.apache.hc.client5.http.utils.Base64;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import ru.dyusov.Gateway.request.UserInfoRequest;

import java.nio.charset.StandardCharsets;

@Service
public class AuthService {

    @Value("${token-uri}")
    private String tokenUri;

    @Value("${auth-uri}")
    private String authUri;

    @Value("${create-user-uri}")
    private String createUserUri;

    @Value("${authorization-grant-type}")
    private String grantType;

    private String hashPassword(String password){
        return BCrypt.hashpw(password, BCrypt.gensalt(10));
    }

    HttpHeaders createHeaders(String clientId, String clientSecret){
        return new HttpHeaders() {{
            String auth = clientId + ":" + clientSecret;
            byte[] encodedAuth = Base64.encodeBase64(
                    auth.getBytes(StandardCharsets.US_ASCII) );
            String authHeader = "Basic " + new String( encodedAuth );
            set("Authorization", authHeader );
            setContentType(MediaType.valueOf(MediaType.APPLICATION_FORM_URLENCODED_VALUE));
        }};
    }

    public String login(String username, String password, String clientId, String clientSecret) {
        MultiValueMap<String, String> fields = new LinkedMultiValueMap();
        fields.add("username", username);
        fields.add("password", password);
        fields.add("grant_type", grantType);
        return new RestTemplate().exchange(tokenUri,
                HttpMethod.POST,
                new HttpEntity<>(fields ,createHeaders(clientId, clientSecret)),
                String.class).getBody();
    }

    public UserInfoResponse auth(String authHeader) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", authHeader);
        System.out.println(authHeader);
        return new RestTemplate().exchange(
                authUri,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                UserInfoResponse.class
        ).getBody();
    }

    public String createUser(UserInfoRequest user, String authHeader) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", authHeader);
        user.password = hashPassword(user.password);
        return new RestTemplate().postForEntity(
                createUserUri,
                new HttpEntity<>(user, headers),
                String.class).getBody();
    }
}
