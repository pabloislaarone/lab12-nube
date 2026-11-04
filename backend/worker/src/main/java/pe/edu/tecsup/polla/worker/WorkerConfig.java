package pe.edu.tecsup.polla.worker;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pe.edu.tecsup.polla.events.ScoringEvent;
import pe.edu.tecsup.polla.scoring.ScoringEngine;

@Configuration
public class WorkerConfig {

    @Bean
    public ScoringEngine scoringEngine(WorkerProperties props) {
        return new ScoringEngine(props.scoring().toConfig());
    }

    @Bean
    public TopicExchange pollaExchange() {
        return new TopicExchange(ScoringEvent.EXCHANGE, true, false);
    }

    @Bean
    public Queue scoringQueue() {
        return QueueBuilder.durable(ScoringEvent.QUEUE).build();
    }

    @Bean
    public Binding scoringBinding(Queue scoringQueue, TopicExchange pollaExchange) {
        return BindingBuilder.bind(scoringQueue).to(pollaExchange).with(ScoringEvent.ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        // Usa el tipo del parámetro del @RabbitListener en lugar del header __TypeId__
        converter.setAlwaysConvertToInferredType(true);
        return converter;
    }
}
