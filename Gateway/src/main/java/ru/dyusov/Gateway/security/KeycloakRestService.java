package ru.dyusov.Gateway.security;

import org.apache.hc.client5.http.utils.Base64;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Service
public class KeycloakRestService {

    @Value("${token-uri}")
    private String tokenUri;

    @Value("${authorization-grant-type}")
    private String grantType;

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
        System.out.println(createHeaders(clientId, clientSecret).get("Authorization"));
        return new RestTemplate().exchange(tokenUri,
                HttpMethod.POST,
                new HttpEntity<>(fields ,createHeaders(clientId, clientSecret)),
                String.class).getBody();
    }

    public String refreshToken(String refreshToken) {
        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
//        map.add("client_id", clientId);
        map.add("grant_type", "refresh_token");
        map.add("refresh_token", refreshToken);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, new HttpHeaders());
        return new RestTemplate().postForObject(tokenUri, request, String.class);
    }

//    public UserInfoResponse getUserInfo(String authToken) {
//        HttpHeaders headers = new HttpHeaders();
//        headers.set("Authorization", "Bearer " + authToken);
//        return new RestTemplate().exchange(
//                keycloakUserInfo,
//                HttpMethod.GET,
//                new HttpEntity<>(headers),
//                UserInfoResponse.class
//        ).getBody();
//    }
}
