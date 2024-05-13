package ru.dyusov.Gateway.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor(staticName = "build")
@NoArgsConstructor
public class UserInfoRequest {
    public Long id;
    public String email;
    public String password;
    public String authorities;
    public Boolean enabled = false;
}
