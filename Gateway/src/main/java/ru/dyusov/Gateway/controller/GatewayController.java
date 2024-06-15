package ru.dyusov.Gateway.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import ru.dyusov.Gateway.kafka.KafkaProducer;
import ru.dyusov.Gateway.kafka.LogMessage;
import ru.dyusov.Gateway.request.AddTicketRequest;
import ru.dyusov.Gateway.request.PrivilegeHistoryRequest;
import ru.dyusov.Gateway.request.TicketRequest;
import ru.dyusov.Gateway.response.*;
import ru.dyusov.Gateway.security.AuthService;
import ru.dyusov.Gateway.security.UserInfoResponse;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static java.lang.Thread.sleep;

@RestController
@RequestMapping("/api/v1")
@PropertySource("classpath:application.properties")
public class GatewayController {

    @Value("${flight_service.host}")
    private String FLIGHT_SERVICE;

    @Value("${ticket_service.host}")
    private String TICKET_SERVICE;

    @Value("${bonus_service.host}")
    private String BONUS_SERVICE;

    @Value("${statistics_service.host}")
    private String STATISTICS_SERVICE;

    private static String GET_FLIGHTS_URL = "/api/flight/flights?page={page}&size={size}";
    private static String GET_FLIGHT_BY_NUMBER_URL = "/api/flight/flights/{flightNumber}";
    private static String GET_TICKETS_URL = "/api/ticket/tickets";
    private static String GET_TICKET_BY_UID = "/api/ticket/tickets/{ticketUid}";
    private static String GET_PRIVILEGE_URL = "/api/bonus/privilege";
    private static String GET_PRIVILEGE_HISTORY_URL = "/api/bonus/privilege/history";
    private static String GET_PRIVILEGE_HISTORY_BY_TICKET_UID_URL = "/api/bonus/privilege/history/{ticketUid}";
    private static String GET_STATS_URL = "/stats";

    @Autowired
    private RetryTemplate retryTemplate;

    @Autowired
    private AuthService authService;

    @Autowired
    private KafkaProducer kafkaProducer;

    @GetMapping("/authOnly")
    public ResponseEntity<UserInfoResponse> test(@RequestHeader("Authorization") String authHeader) throws Exception {
        // for test
        LocalDateTime startDttm = LocalDateTime.now();

        String username = authService.auth(authHeader).getPrincipal();
//        kafkaProducer.send(new LogMessageDto(UUID.randomUUID(), startDttm, LocalDateTime.now(), username, "AUTH", "GATEWAY"));
        return new ResponseEntity<>(authService.auth("Bearer " + authService.getAuthToken()), HttpStatus.OK);
    }

    @GetMapping("/stats")
    public LogMessage[] getStatistics(@RequestHeader("Authorization") String authHeader) {
        String username = authService.auth(authHeader).getPrincipal();
        LocalDateTime startDttm = LocalDateTime.now();
        LogMessage[] response = new RestTemplate().exchange(
                STATISTICS_SERVICE + GET_STATS_URL,
                HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()),
                LogMessage[].class).getBody();
        // send to kafka
        kafkaProducer.send(new LogMessage(UUID.randomUUID(), startDttm, LocalDateTime.now(), username, "GET_FLIGHTS", "FLIGHT_SERVICE"));
        return response;
    }

    @GetMapping("/flights")
    public FlightListResponse testFlights(@RequestParam int page, @RequestParam int size, @RequestHeader("Authorization") String authHeader) throws Exception {
        String username = authService.auth(authHeader).getPrincipal();
        LocalDateTime startDttm = LocalDateTime.now();
        FlightListResponse response = new RestTemplate().exchange(
                FLIGHT_SERVICE + GET_FLIGHTS_URL,
                HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()),
                FlightListResponse.class,
                page - 1,
                size).getBody();
        // send to kafka
        kafkaProducer.send(new LogMessage(UUID.randomUUID(), startDttm, LocalDateTime.now(), username, "GET_FLIGHTS", "FLIGHT_SERVICE"));
        return response;
    }

    @PostMapping("/tickets")
    public ResponseEntity<TicketPurchaseResponse> addTicket(@RequestBody TicketRequest ticket, @RequestHeader("Authorization") String authHeader) throws Exception {
        // validate token and get username
        String username = authService.auth(authHeader).getPrincipal();
        LocalDateTime startDttm = LocalDateTime.now();
        // get flight by flightNumber and check if exist
        FlightResponse flight = new RestTemplate().exchange(
                FLIGHT_SERVICE + GET_FLIGHT_BY_NUMBER_URL,
                HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()),
                FlightResponse.class,
                ticket.getFlightNumber()).getBody();
        // add ticket to Tickets of user

        // send to kafka
        kafkaProducer.send(new LogMessage(UUID.randomUUID(), startDttm, LocalDateTime.now(), username, "GET_FLIGHT_BY_NUMBER_URL", "FLIGHT_SERVICE"));
        // reassign start dttm
        startDttm = LocalDateTime.now();
        AddTicketRequest request = AddTicketRequest.build(
                username,
                ticket.getFlightNumber(),
                ticket.getPrice(),
                "PAID"
        );
        ResponseEntity<UUID> addedTicket = new RestTemplate().postForEntity(
                TICKET_SERVICE + GET_TICKETS_URL,
                request,
                UUID.class);
        // send to kafka
        kafkaProducer.send(new LogMessage(UUID.randomUUID(), startDttm, LocalDateTime.now(), username, "GET_TICKETS", "TICKET_SERVICE"));
        // get ticketUid of added ticket
        UUID ticketUid = addedTicket.getBody();

        try {
            PrivilegeResponse privilege = getPrivilegeInfo(username).getBody();
            int bonusBalance = privilege.getBalance();

            int paidByMoney = ticket.getPrice();
            int paidByBonuses = 0;

            if (ticket.isPaidFromBalance()) {
                // debit all bonuses
                addHistoryRecord(ticketUid, bonusBalance, "DEBIT_THE_ACCOUNT", username);
                updateBalance(0, username);
                paidByBonuses = bonusBalance;
                paidByMoney = paidByMoney - bonusBalance;
            } else {
                // add bonus = 10% of ticket price
                addHistoryRecord(ticketUid, ticket.getPrice() / 10, "FILL_IN_BALANCE", username);
                updateBalance(bonusBalance + (ticket.getPrice() / 10), username);
            }

            // get updated balance info
            PrivilegeResponse updatedPrivilege = getPrivilegeInfo(username).getBody();

            // create response
            TicketPurchaseResponse response = TicketPurchaseResponse.build(
                    ticketUid,
                    flight.getFlightNumber(),
                    flight.getFromAirport(),
                    flight.getToAirport(),
                    flight.getDate(),
                    flight.getPrice(),
                    paidByMoney,
                    paidByBonuses,
                    "PAID",
                    updatedPrivilege
            );
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            if (e.getClass() == HttpClientErrorException.class) {
                throw new Exception("Privilege of user " + username + " not found");
            } else if (e.getClass() == ResponseStatusException.class) {
                // delete recently added ticket
                HttpHeaders headers = new HttpHeaders();
                headers.set("X-User-Name", username);
                RestTemplate rest = new RestTemplate();
                rest.setRequestFactory(new HttpComponentsClientHttpRequestFactory());
                startDttm = LocalDateTime.now();
                rest.exchange(
                        TICKET_SERVICE + GET_TICKET_BY_UID,
                        HttpMethod.DELETE,
                        new HttpEntity<>(headers),
                        Void.class,
                        ticketUid,
                        username
                );
                // send to kafka
                kafkaProducer.send(new LogMessage(UUID.randomUUID(), startDttm, LocalDateTime.now(), username, "DELETE_TICKET", "TICKET_SERVICE"));
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Bonus Service unavailable");
            } else {
                throw new Exception(e.getMessage());
            }
        }
    }

    private ResponseEntity<Void> flightServiceFallback(Throwable throwable) {
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Flight Service unavailable");
    }

    @GetMapping("/tickets")
    public TicketResponse[] getTickets(@RequestHeader("Authorization") String authHeader) throws Exception {
        // validate token and get username
        String username = authService.auth(authHeader).getPrincipal();

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Name", username);
        TicketResponse[] tickets = new RestTemplate().exchange(
                TICKET_SERVICE + GET_TICKETS_URL,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                TicketResponse[].class).getBody();
        for (TicketResponse ticket : tickets) {
            FlightResponse flight = new RestTemplate().exchange(
                    FLIGHT_SERVICE + GET_FLIGHT_BY_NUMBER_URL,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    FlightResponse.class,
                    ticket.getFlightNumber()).getBody();
            ticket.setDate(flight.getDate());
            ticket.setFromAirport(flight.getFromAirport());
            ticket.setToAirport(flight.getToAirport());
        }
        return tickets;
    }

    private ResponseEntity<Void> ticketServiceFallback(Throwable throwable) {
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Ticket Service unavailable");
    }


    @GetMapping("/me")
    public ResponseEntity getUserInfo(@RequestHeader("Authorization") String authHeader) throws Exception {
        // validate token and get username
        String username = authService.auth(authHeader).getPrincipal();

        TicketResponse[] tickets = getTickets(authHeader);
        PrivilegeResponse privilege = getPrivilegeInfo(username).getBody();
        Map<String, Object> body = new HashMap<>();
        body.put("tickets", tickets);
        if (privilege != null) {
            body.put("privilege", privilege);
        } else {
            body.put("privilege", "");
        }
        return new ResponseEntity<>(body, HttpStatus.OK);
    }

    @GetMapping("/tickets/{ticketUid}")
    public TicketResponse getTicketByUid(@PathVariable UUID ticketUid, @RequestHeader("Authorization") String authHeader) throws Exception {
        // validate token and get username
        String username = authService.auth(authHeader).getPrincipal();
        LocalDateTime startDttm = LocalDateTime.now();

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Name", username);
        TicketResponse ticket = new RestTemplate().exchange(
                TICKET_SERVICE + GET_TICKET_BY_UID,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                TicketResponse.class,
                ticketUid,
                username
        ).getBody();
        // send to kafka
        kafkaProducer.send(new LogMessage(UUID.randomUUID(), startDttm, LocalDateTime.now(), username, "GET_TICKET", "TICKET_SERVICE"));

        startDttm = LocalDateTime.now();
        FlightResponse flight = new RestTemplate().exchange(
                FLIGHT_SERVICE + GET_FLIGHT_BY_NUMBER_URL,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                FlightResponse.class,
                ticket.getFlightNumber()).getBody();
        // send to kafka
        kafkaProducer.send(new LogMessage(UUID.randomUUID(), startDttm, LocalDateTime.now(), username, "GET_FLIGHT_BY_NUMBER", "FLIGHT_SERVICE"));

        ticket.setDate(flight.getDate());
        ticket.setFromAirport(flight.getFromAirport());
        ticket.setToAirport(flight.getToAirport());
        return ticket;
    }

    @DeleteMapping("/tickets/{ticketUid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> deleteTicketByUid(@PathVariable UUID ticketUid, @RequestHeader("Authorization") String authHeader) throws Exception {
        // validate token and get username
        String username = authService.auth(authHeader).getPrincipal();
        LocalDateTime startDttm = LocalDateTime.now();

        try {
            // get ticket to be deleted
            TicketResponse ticket = getTicketByUid(ticketUid, authHeader);

            // change status to "CANCELED"
            HttpHeaders ticketHeaders = new HttpHeaders();
            ticketHeaders.set("X-User-Name", username);
            Map<String, Object> fields = new HashMap<>();
            fields.put("status", "CANCELED");
            RestTemplate restTemplate = new RestTemplate();
            restTemplate.setRequestFactory(new HttpComponentsClientHttpRequestFactory());
            restTemplate.exchange(
                    TICKET_SERVICE + GET_TICKET_BY_UID,
                    HttpMethod.PATCH,
                    new HttpEntity<>(fields, ticketHeaders),
                    TicketResponse.class,
                    ticketUid
            );
            // send to kafka
            kafkaProducer.send(new LogMessage(UUID.randomUUID(), startDttm, LocalDateTime.now(), username, "GET_TICKET_BY_UID", "TICKET_SERVICE"));

            // call Bonus Service to update data in separate thread
            new Thread(() -> {
                try {
                    retryTemplate.execute(obj -> {
                        System.out.println("Call bonus service...");
                        updateDataInBonusService(ticketUid, username);
                        System.out.println("Bonus service updated!");
                        return null;
                    });
                } catch (Exception e) {
                    try {
                        sleep(10);
                        retryTemplate.execute(obj -> {
                            System.out.println("Call bonus service...");
                            updateDataInBonusService(ticketUid, username);
                            System.out.println("Bonus service updated!");
                            return null;
                        });
                    } catch (Exception ex) {
                        try {
                            sleep(20);
                            retryTemplate.execute(obj -> {
                                System.out.println("Call bonus service...");
                                updateDataInBonusService(ticketUid, username);
                                System.out.println("Bonus service updated!");
                                return null;
                            });
                        } catch (Exception exc) {
                            try {
                                sleep(20);
                                retryTemplate.execute(obj -> {
                                    System.out.println("Call bonus service...");
                                    updateDataInBonusService(ticketUid, username);
                                    System.out.println("Bonus service updated!");
                                    return null;
                                });
                            } catch (Exception exception) {
                                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Bonus service unavailable");
                            }
                        }
                    }
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Bonus service unavailable");
                }
            }).start();

        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatusCode.valueOf(404)) {
                throw new Exception("Ticket with of user " + username + " not found");
            } else {
                throw new Exception("Unknown error" + e.getMessage());
            }
        }
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    private void updateDataInBonusService(UUID ticketUid, String username) throws Exception {
        LocalDateTime startDttm = LocalDateTime.now();
        // find privilege status
        PrivilegeResponse privilege = getPrivilegeInfo(username).getBody();
        int currentBalance = privilege.getBalance();

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Name", username);

        // find history record of this ticketUid
        PrivilegeHistoryResponse privilegeHistoryResponse =
                new RestTemplate().exchange(
                        BONUS_SERVICE + GET_PRIVILEGE_HISTORY_BY_TICKET_UID_URL,
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        PrivilegeHistoryResponse.class,
                        ticketUid
                ).getBody();
        // send to kafka
        kafkaProducer.send(new LogMessage(UUID.randomUUID(), startDttm, LocalDateTime.now(), username, "GET_PRIVILEGE_HISTORY_BY_TICKET_UID", "BONUS_SERVICE"));

        String operationType = privilegeHistoryResponse.getOperationType();
        int balanceDiff = privilegeHistoryResponse.getBalanceDiff();
        int newBalance;
        // refund bonuses
        if (operationType.equals("FILL_IN_BALANCE")) {
            newBalance = currentBalance - balanceDiff;
            operationType = "DEBIT_THE_ACCOUNT";
        } else if (operationType.equals("DEBIT_THE_ACCOUNT")) {
            newBalance = currentBalance + balanceDiff;
            operationType = "FILL_IN_BALANCE";
        } else {
            throw new Exception("Unknown type of operation");
        }
        // add history record
        addHistoryRecord(ticketUid, balanceDiff, operationType, username);

        // change bonus balance of user
        Map<String, Object> balanceUpdate = new HashMap<>();
        balanceUpdate.put("balance", newBalance);
        RestTemplate rest = new RestTemplate();
        rest.setRequestFactory(new HttpComponentsClientHttpRequestFactory());
        startDttm = LocalDateTime.now();
        ResponseEntity<PrivilegeResponse> updatedPrivilege = rest.exchange(
                BONUS_SERVICE + GET_PRIVILEGE_URL,
                HttpMethod.PATCH,
                new HttpEntity<>(balanceUpdate, headers),
                PrivilegeResponse.class
        );
        // send to kafka
        kafkaProducer.send(new LogMessage(UUID.randomUUID(), startDttm, LocalDateTime.now(), username, "UPDATE_PRIVILEGE_INFO", "BONUS_SERVICE"));
    }

    @GetMapping("/privilege")
    public ResponseEntity<PrivilegeWithHistoryResponse> getPrivilegeWithHistory(@RequestHeader("Authorization") String authHeader) throws Exception {
        // validate token and get username
        String username = authService.auth(authHeader).getPrincipal();

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Name", username);
        ResponseEntity<PrivilegeResponse> privilege = getPrivilegeInfo(username);
        ResponseEntity<PrivilegeHistoryResponse[]> historyList = getPrivilegeHistory(username);
        PrivilegeWithHistoryResponse response = PrivilegeWithHistoryResponse.build(privilege.getBody().getBalance(), privilege.getBody().getStatus(), Arrays.asList(historyList.getBody()));
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @GetMapping("/manage/health")
    public ResponseEntity<Void> status() {
        return new ResponseEntity<>(HttpStatus.OK);
    }

    private ResponseEntity<PrivilegeHistoryResponse[]> getPrivilegeHistory(String username) {
        LocalDateTime startDttm = LocalDateTime.now();
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Name", username);
        ResponseEntity<PrivilegeHistoryResponse[]> response = new RestTemplate().exchange(
                BONUS_SERVICE + GET_PRIVILEGE_HISTORY_URL,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                PrivilegeHistoryResponse[].class
        );
        // send to kafka
        kafkaProducer.send(new LogMessage(UUID.randomUUID(), startDttm, LocalDateTime.now(), username, "GET_PRIVILEGE_HISTORY", "BONUS_SERVICE"));
        return response;
    }

    private ResponseEntity<PrivilegeResponse> getPrivilegeInfo(String username) {
        LocalDateTime startDttm = LocalDateTime.now();
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Name", username);
        ResponseEntity<PrivilegeResponse> response = new RestTemplate().exchange(
                BONUS_SERVICE + GET_PRIVILEGE_URL,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                PrivilegeResponse.class
        );
        // send to kafka
        kafkaProducer.send(new LogMessage(UUID.randomUUID(), startDttm, LocalDateTime.now(), username, "GET_PRIVILEGE_INFO", "BONUS_SERVICE"));
        return response;
    }

    private ResponseEntity<Void> bonusServiceFallback(Throwable throwable) {
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Bonus Service unavailable");
    }

    private String addHistoryRecord(UUID ticketUid, int bonusAmount, String operationType, String username) {
        LocalDateTime startDttm = LocalDateTime.now();
        PrivilegeHistoryRequest request = PrivilegeHistoryRequest.build(
                ticketUid,
                bonusAmount,
                operationType
        );
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Name", username);
        HttpEntity<PrivilegeHistoryRequest> historyRecord = new HttpEntity<>(request, headers);
        ResponseEntity<Void> historyResponseEntity = new RestTemplate().postForEntity(
                BONUS_SERVICE + GET_PRIVILEGE_HISTORY_URL,
                historyRecord,
                Void.class
        );
        // send to kafka
        kafkaProducer.send(new LogMessage(UUID.randomUUID(), startDttm, LocalDateTime.now(), username, "ADD_HISTORY_RECORD", "BONUS_SERVICE"));
        return historyResponseEntity.getHeaders().get("Location").toString();
    }

    private void updateBalance(int balance, String username) {
        LocalDateTime startDttm = LocalDateTime.now();
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Name", username);
        Map<String, Object> fields = new HashMap<>();
        fields.put("balance", balance);
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setRequestFactory(new HttpComponentsClientHttpRequestFactory());
        restTemplate.exchange(
                BONUS_SERVICE + GET_PRIVILEGE_URL,
                HttpMethod.PATCH,
                new HttpEntity<>(fields, headers),
                PrivilegeResponse.class
        );
        // send to kafka
        kafkaProducer.send(new LogMessage(UUID.randomUUID(), startDttm, LocalDateTime.now(), username, "UPDATE_BALANCE", "BONUS_SERVICE"));
    }
}
