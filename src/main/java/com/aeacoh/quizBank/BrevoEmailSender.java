package com.aeacoh.quizBank;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class BrevoEmailSender {
    private final Logger logger = LoggerFactory.getLogger(BrevoEmailSender.class);

    @Value("${brevo.api.key}")
    private String apiKey;

    @Value("${brevo.sender.email}")
    private String senderEmail;

    @Value("${brevo.sender.name}")
    private String senderName;

    private final RestTemplate restTemplate = new RestTemplate();

    public void sendEmail(String receiveMail, String title, String content) {
        String url = "https://api.brevo.com/v3/smtp/email";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("api-key", apiKey);

        Map<String, Object> body = new HashMap<>();
        
        // 발신자 정보
        Map<String, String> sender = new HashMap<>();
        sender.put("name", senderName);
        sender.put("email", senderEmail);
        body.put("sender", sender);

        // 수신자 정보
        Map<String, String> receiver = new HashMap<>();
        receiver.put("email", receiveMail);
        body.put("to", Collections.singletonList(receiver));

        // 제목 및 내용
        body.put("subject", title);
        body.put("htmlContent", content);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            if (response.getStatusCode() == HttpStatus.CREATED) {
                logger.info("메일 발송 성공!");
            }
        } catch (Exception e) {
            logger.error("메일 발송 중 오류 발생: " + e.getMessage());
        }
    }
}
