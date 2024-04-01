package ru.dyusov.Gateway.security;

import com.auth0.jwk.Jwk;
import com.auth0.jwk.UrlJwkProvider;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.net.URL;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;

@Service
public class JwtService {

    @Value("${keycloak.jwk-set-uri}")
    private String jwksUrl;

    @Value("${keycloak.certs-id}")
    private String certsId;

    @Cacheable(value = "jwkCache")
    public Jwk getJwk() throws Exception {
        return new UrlJwkProvider(new URL(jwksUrl)).get(certsId);
    }

    public void validate(String authHeader) throws Exception {
        DecodedJWT jwt = JWT.decode(authHeader.replace("Bearer", "").trim());

        // check JWT is valid
        Jwk jwk = getJwk();
        Algorithm algorithm = Algorithm.RSA256((RSAPublicKey) jwk.getPublicKey(), null);

        algorithm.verify(jwt);

        // check JWT is still active
        Date expiryDate = jwt.getExpiresAt();
        if (expiryDate.before(new Date())) throw new Exception("token is expired");
    }

}