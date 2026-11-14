package io.appform.dropwizard.actors.messaging;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ShutdownSignalException;
import io.appform.dropwizard.actors.common.RabbitmqActorException;
import java.util.Objects;
import lombok.experimental.UtilityClass;

/**
 * Decides whether a closed publish channel should be replaced.
 *
 * <p>Connections use automatic recovery: when a connection fails, the client restores the connection and every channel
 * on it, in place. Opening a replacement channel during that recovery races with it: the client can recover the new
 * channel as well, leaving an extra, unused channel open on the connection after every outage. So a channel closed by a
 * connection failure (a hard error) is left to automatic recovery, and publishing fails fast until it is restored.
 *
 * <p>A channel closed on its own (a channel-level broker error such as a missing exchange, or a confirm timeout) is not
 * recovered by the client, so it is replaced.
 */
@UtilityClass
class ChannelRecovery {

    /**
     * @throws RabbitmqActorException if {@code channel} was closed by a connection failure, which automatic recovery
     *                                will repair
     */
    void failIfAwaitingConnectionRecovery(final Channel channel, final String target) {
        if (Objects.isNull(channel)) {
            return;
        }
        final ShutdownSignalException reason = channel.getCloseReason();
        if (Objects.nonNull(reason) && reason.isHardError()) {
            throw RabbitmqActorException.propagate(String.format(
                    "Connection for %s is down; waiting for automatic recovery: %s", target, reason.getMessage()),
                    reason);
        }
    }

    /**
     * Describes why {@code channel} was closed, for logging.
     */
    String describeCloseReason(final Channel channel) {
        if (Objects.isNull(channel)) {
            return "no channel";
        }
        final ShutdownSignalException reason = channel.getCloseReason();
        return Objects.isNull(reason) ? "unknown reason" : reason.getMessage();
    }
}
