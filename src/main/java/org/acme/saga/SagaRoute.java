package org.acme.saga;

import javax.inject.Inject;

import org.acme.service.CreditService;
import org.acme.service.OrderService;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.SagaPropagation;
import org.apache.camel.model.rest.RestParamType;
import org.apache.camel.saga.InMemorySagaService;
import org.apache.camel.service.lra.LRASagaService;

public class SagaRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        restConfiguration();
        //CamelSagaService sagaService = new InMemorySagaService();


        LRASagaService sagaService = new LRASagaService();
        sagaService.setCoordinatorUrl("http://localhost:8080/");
        sagaService.setLocalParticipantUrl("http://localhost:8082/");
        getContext().addService(sagaService);


        //getContext().addService(new InMemorySagaService());

        rest("/buy")
                .post()
                    .param()
                        .name("amount")
                        .type(RestParamType.header)
                        .required(true)
                    .endParam()
                .route()
                .log("Iniciando saga")
                .to("direct:saga");

        from("direct:saga")
                .saga().propagation(SagaPropagation.REQUIRES_NEW)
                .log("Creating a new order")
                .to("direct:createOrder").log("Taking the credit")
                .to("direct:reserveCredit").log("Finalizing")
                .log("Done!");

        // Order service
        from("direct:createOrder")
                .saga()
                    .propagation(SagaPropagation.MANDATORY)
                    .completion("direct:finalize")
                    .compensation("direct:cancelOrder")
                .transform().header(Exchange.SAGA_LONG_RUNNING_ACTION)
                .bean("OrderService", "createOrder").log("Order ${body} created");

        from("direct:cancelOrder")
                .transform().header(Exchange.SAGA_LONG_RUNNING_ACTION)
                .bean("OrderService", "cancelOrder").log("Order ${body} cancelled");

        // Final actions
        from("direct:finalize")
                .log("completo satisfactorio").end();

        // Credit service
        from("direct:reserveCredit")
                .saga()
                    .propagation(SagaPropagation.MANDATORY)
                    .compensation("direct:refundCredit")
                .transform().header(Exchange.SAGA_LONG_RUNNING_ACTION)
                .bean("CreditService", "reserveCredit")
                .log("Credit ${header.amount} reserved in action ${body}");

        from("direct:refundCredit").transform().header(Exchange.SAGA_LONG_RUNNING_ACTION)
                .bean("CreditService", "refundCredit").log("Credit for action ${body} refunded");

    }
}
