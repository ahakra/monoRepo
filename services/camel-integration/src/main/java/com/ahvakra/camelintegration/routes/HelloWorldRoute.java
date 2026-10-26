package com.ahvakra.camelintegration.routes;

import com.ahvakra.camelintegration.processors.TimeStampProcessor;
import java.util.Objects;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

@Component
public class HelloWorldRoute extends RouteBuilder {

    @Override
    public void configure() {
        from("timer:example?period=5000")
            .routeId("hello-world-route")
            .setBody(constant("Hello world"))
            .log("BEFORE: ${routeId}, ${body}")
            .to("direct:processHello")
            .log("AFTER: ${routeId}, ${body}");

        from("direct:processHello")
            .routeId("hello-world-processing-route")
            .log("PROCESSING: ${routeId}, ${body}")
            .process(exchange -> {
                String body = exchange.getMessage().getBody(String.class);

                exchange.getMessage().setBody(Objects.requireNonNull(body).toUpperCase());
            })
            .bean(TimeStampProcessor.class, "addTimeStamp")
            .log("FINISHED: ${routeId}, ${body}");
    }
}
