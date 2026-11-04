package pe.edu.tecsup.marcador.api.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import pe.edu.tecsup.marcador.api.realtime.RankingStream;
import pe.edu.tecsup.marcador.events.ScoringEvent;
import pe.edu.tecsup.marcador.scoring.ScoringEngine;

@Configuration
public class CoreConfig {

    /** Canal de Redis donde el worker avisa que el ranking cambió. */
    public static final String RANKING_CHANNEL = "marcador:ranking-updated";

    @Bean
    public ScoringEngine scoringEngine(AppProperties props) {
        return new ScoringEngine(props.scoring().toConfig());
    }

    // ---------- RabbitMQ ----------

    @Bean
    public TopicExchange marcadorExchange() {
        return new TopicExchange(ScoringEvent.EXCHANGE, true, false);
    }

    @Bean
    public Queue scoringQueue() {
        return QueueBuilder.durable(ScoringEvent.QUEUE).build();
    }

    @Bean
    public Binding scoringBinding(Queue scoringQueue, TopicExchange marcadorExchange) {
        return BindingBuilder.bind(scoringQueue).to(marcadorExchange).with(ScoringEvent.ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    // ---------- Redis pub/sub para tiempo real ----------

    @Bean
    public RedisMessageListenerContainer redisListenerContainer(RedisConnectionFactory factory,
                                                                RankingStream rankingStream) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        container.addMessageListener(rankingStream, new ChannelTopic(RANKING_CHANNEL));
        return container;
    }
}
