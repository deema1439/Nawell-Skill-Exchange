package com.example.capstone_3.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Service
public class WhatsAppService {
    @Value("${whatsapp.instance-id}")
    private String instanceId;

    @Value("${whatsapp.token}")
    private String token;

    private final RestTemplate restTemplate = new RestTemplate();

    public void sendMessage(String phone, String message) {
        if (phone.startsWith("05")) {
            phone = "966" + phone.substring(1);
        }

        String url = "https://api.ultramsg.com/" + instanceId + "/messages/chat";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("token", token);
        body.add("to", phone);
        body.add("body", message);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        try {
            String response = restTemplate.postForObject(url, request, String.class);
            System.out.println("UltraMsg response: " + response);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send WhatsApp message: " + e.getMessage());
        }
    }



}
