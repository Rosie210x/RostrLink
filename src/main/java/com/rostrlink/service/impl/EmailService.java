package com.rostrlink.service.impl;

import java.util.Map;

public class EmailService {
    private final SesClient ses;

    public EmailService(SesClient ses) { this.ses = ses; }

    public String sendTemplatedEmail(String to, String templateName, Map<String, String> templateData, String from) {
        Destination dest = Destination.builder().toAddresses(to).build();
        SendTemplatedEmailRequest req = SendTemplatedEmailRequest.builder()
                .destination(dest)
                .source(from)
                .template(templateName)
                .templateData(new ObjectMapper().writeValueAsString(templateData))
                .build();
        SendTemplatedEmailResponse resp = ses.sendTemplatedEmail(req);
        return resp.messageId();
    }
}
