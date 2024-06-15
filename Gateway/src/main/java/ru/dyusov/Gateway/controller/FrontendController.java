package ru.dyusov.Gateway.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import ru.dyusov.Gateway.kafka.LogMessage;
import ru.dyusov.Gateway.request.TicketRequest;
import ru.dyusov.Gateway.request.UserInfoRequest;
import ru.dyusov.Gateway.response.*;
import ru.dyusov.Gateway.security.AuthService;
import ru.dyusov.Gateway.security.OAuthTokenRequest;
import ru.dyusov.Gateway.security.UserInfoResponse;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/")
public class FrontendController {

    @Autowired
    private AuthService authService;

    @Autowired
    private GatewayController controller;

    @GetMapping("/")
    public String home(@ModelAttribute OAuthTokenRequest token, Model model) {
        try {
            String username = authService.auth("Bearer " + authService.getAuthToken()).getPrincipal();
            model.addAttribute("greeting", "Hello, " + username);
            return "index";
        } catch (Exception e) {
            return "login";
        }
    }

    @PostMapping(value = "/auth", params = "action=login")
    public String login(@ModelAttribute OAuthTokenRequest token, Model model) {
        authService.login(token.getUsername(), token.getPassword(), "rsoi-client", "rsoi-client-secret");
        String username = authService.auth("Bearer " + authService.getAuthToken()).getPrincipal();
        System.out.println("got here!");
        model.addAttribute("greeting", "Hello, " + username);
        return "index";
    }

    @PostMapping(value = "/auth", params = "action=register")
    public String register(@ModelAttribute OAuthTokenRequest token, Model model) {
        UserInfoRequest userInfo = new UserInfoRequest();
        userInfo.setEmail(token.getUsername());
        userInfo.setPassword(token.getPassword());
        userInfo.setAuthorities("USER");
        userInfo.setEnabled(true);
        authService.createUser(userInfo);
        authService.login(token.getUsername(), token.getPassword(), "rsoi-client", "rsoi-client-secret");
        String username = authService.auth("Bearer " + authService.getAuthToken()).getPrincipal();
        System.out.println("got here!");
        model.addAttribute("greeting", "Hello, " + username);
        return "index";
    }

    @GetMapping("/logout")
    public String logout(@ModelAttribute OAuthTokenRequest token, Model model) {
        authService.logout();
        return "login";
    }

    @GetMapping("/testAuth")
    public ResponseEntity<UserInfoResponse> test() {
        try {
            String token = authService.getAuthToken();
            return new ResponseEntity<>(authService.auth("Bearer " + token), HttpStatus.OK);
        } catch (Exception e) {
            throw new HttpClientErrorException(HttpStatus.UNAUTHORIZED, "INVALID TOKEN");
        }
    }

    @GetMapping("/flights")
    public String testFlights(@RequestParam int page, @RequestParam int size, Model model) throws Exception {
        FlightListResponse flightListResponse = controller.testFlights(page, size, "Bearer " + authService.getAuthToken());
        model.addAttribute("items", flightListResponse.getItems());
        model.addAttribute("totalElements", flightListResponse.getTotalElements());
        model.addAttribute("page", page);
        model.addAttribute("size", size);
        return "flights";
    }

    @GetMapping("/tickets")
    public String buyTicket(@RequestParam("flightNumber") String flightNumber,
                            @RequestParam("flightPrice") Integer flightPrice,
                            Model model) throws Exception {
        TicketRequest request = TicketRequest.build(flightNumber, flightPrice, false);
        TicketPurchaseResponse purchase = controller.addTicket(request, "Bearer " + authService.getAuthToken()).getBody();
        model.addAttribute("purchased", purchase);
        model.addAttribute("deleted", null);
        model.addAttribute("paid", true);
        return "ticketPurchase";
    }

    @GetMapping("/myTickets")
    public String myTickets(Model model) throws Exception {
        TicketResponse[] tickets = controller.getTickets("Bearer " + authService.getAuthToken());
        model.addAttribute("tickets", new ArrayList<>(Arrays.asList(tickets)));
        return "tickets";
    }

    @GetMapping("/ticket")
    public String ticket(@RequestParam("ticketUid") UUID ticketUid,
                         Model model) throws Exception {
        TicketResponse response = controller.getTicketByUid(ticketUid, "Bearer " + authService.getAuthToken());
        model.addAttribute("ticket", response);
        return "ticketInfo";
    }

    @GetMapping("/info")
    public String info(Model model) throws Exception {
        String username = authService.auth("Bearer " + authService.getAuthToken()).getPrincipal();
        PrivilegeWithHistoryResponse response = controller.getPrivilegeWithHistory("Bearer " + authService.getAuthToken()).getBody();
        model.addAttribute("status", response.getStatus());
        model.addAttribute("balance", response.getBalance());
        model.addAttribute("username", username);
        return "info";
    }

    @GetMapping("/history")
    public String history(Model model) throws Exception {
        String username = authService.auth("Bearer " + authService.getAuthToken()).getPrincipal();
        PrivilegeWithHistoryResponse response = controller.getPrivilegeWithHistory("Bearer " + authService.getAuthToken()).getBody();
        model.addAttribute("username", username);
        model.addAttribute("history", response.getHistory());
        return "history";
    }

    @GetMapping("/tickets/delete")
    public String deleteTicket(@RequestParam("ticketUid") UUID ticketUid,
                               Model model) throws Exception {
        controller.deleteTicketByUid(ticketUid, "Bearer " + authService.getAuthToken());
        TicketResponse deletedTicket = controller.getTicketByUid(ticketUid, "Bearer " + authService.getAuthToken());
        model.addAttribute("purchased", null);
        model.addAttribute("deleted", deletedTicket);
        model.addAttribute("paid", false);
        return "ticketPurchase";
    }

    @GetMapping("/stats")
    public String getStats(Model model) {
        LogMessage[] response = controller.getStatistics("Bearer " + authService.getAuthToken());
        List<LogMessage> messages = new ArrayList<>(Arrays.asList(response));
        List<Stats> stats = new ArrayList<>();
        List<LogMessage> flightMessages = messages.stream().filter(logMessage -> logMessage.service.equals("FLIGHT_SERVICE")).toList();
        List<LogMessage> ticketMessages = messages.stream().filter(logMessage -> logMessage.service.equals("TICKET_SERVICE")).toList();
        List<LogMessage> bonusMessages = messages.stream().filter(logMessage -> logMessage.service.equals("BONUS_SERVICE")).toList();
        Double flightMessageAvgTime = flightMessages.stream().map(item -> ChronoUnit.MILLIS.between(item.eventStart, item.eventEnd)).toList().stream().mapToDouble(d -> d).average().orElse(0.0);
        Double ticketMessageAvgTime = ticketMessages.stream().map(item -> ChronoUnit.MILLIS.between(item.eventStart, item.eventEnd)).toList().stream().mapToDouble(d -> d).average().orElse(0.0);
        Double bonusMessageAvgTime = bonusMessages.stream().map(item -> ChronoUnit.MILLIS.between(item.eventStart, item.eventEnd)).toList().stream().mapToDouble(d -> d).average().orElse(0.0);
        stats.add(Stats.build("FLIGHT_SERVICE", flightMessages.size(), flightMessageAvgTime));
        stats.add(Stats.build("TICKET_SERVICE", ticketMessages.size(), ticketMessageAvgTime));
        stats.add(Stats.build("BONUS_SERVICE", bonusMessages.size(), bonusMessageAvgTime));
        model.addAttribute("stats", stats);
        return "stats";
    }
}
