package ru.dyusov.AuthService.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.dyusov.AuthService.domain.User;
import ru.dyusov.AuthService.service.UserService;

import java.security.Principal;

@RestController
public class UserController {

    @Autowired
    private UserService userService;

    @RequestMapping("/")
    public String home() {
        if (userService.hasProtectedAccess()) {
            return "User is ADMIN!";
        } else {
            return "Hello World";
        }
    }

    @RequestMapping("/user")
    public Principal user(Principal user) {
        return user;
    }

    @RequestMapping("/createUser")
    void createUser(@RequestBody User user) {
        System.out.println("got user: " + user.email);
        userService.saveUser(user);
    }

}
