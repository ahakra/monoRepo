package com.ahvakra.camelintegration.processors;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class TimeStampProcessor {

    public String addTimeStamp(String body) {
        String timestamp = LocalDateTime.now().format(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        );

        return "[" + timestamp + "] " + body.toUpperCase();
    }
}
