package se.kth.id2203.epfd;

import com.google.common.collect.Sets;
import com.oracle.tools.packager.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.kth.id2203.bootstrapping.Bootstrapping;
import se.kth.id2203.network.PL_Deliver;
import se.kth.id2203.network.PL_Send;
import se.kth.id2203.network.Partition;
import se.kth.id2203.network.PerfectLink;
import se.kth.id2203.networking.Message;
import se.kth.id2203.networking.NetAddress;
import se.sics.kompics.*;
import se.sics.kompics.network.Address;
import se.sics.kompics.timer.SchedulePeriodicTimeout;
import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timeout;
import se.sics.kompics.timer.Timer;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class EPFD extends ComponentDefinition {
    private static Logger logger = LoggerFactory.getLogger(EPFD.class);
    protected final Positive<Bootstrapping> boot = requires(Bootstrapping.class);
    protected final Negative<Bootstrapping> boot2 = provides(Bootstrapping.class);

    // component fields
    public final Positive<Timer> timer = requires(Timer.class);
    public final Positive<PerfectLink> perfectLink = requires(PerfectLink.class);
    public final Negative<EventuallyPerfectFailureDetector> epfd = provides(EventuallyPerfectFailureDetector.class);

    private NetAddress self = config().getValue("id2203.project.address", NetAddress.class);
    private Set<NetAddress> topology = new HashSet<>();

    private long delta = 3000;

    //mutable state
    private long period = 5000;
    private Set<NetAddress> alive = new HashSet<>();
    private Set<NetAddress> suspected = new HashSet<>();
    private int seqnum = 0;

    //case class CheckTimeout(timeout: ScheduleTimeout) extends CheckTimeout(timeout);

    private void startTimer(long delay) {
        logger.info("startTimer called with delay: {}", delay);

        ScheduleTimeout scheduledTimeout = new ScheduleTimeout(delay);
        scheduledTimeout.setTimeoutEvent(new CheckTimeout(scheduledTimeout));
        trigger(scheduledTimeout, timer);
    }

    Handler<AllNodes> initHandler = new Handler<AllNodes>(){
        @Override
        public void handle(AllNodes all) {
            logger.info("EFPD Init: {}", all.nodes);
            seqnum = 0;
            topology = new HashSet<>(all.nodes);
            topology.remove(self);
            alive =  new HashSet<>(topology); // assume everyone is alive in the beginning
            suspected = new HashSet<>();
            startTimer(period);
        }
    };


    protected final Handler<Timeout> timeoutHandler = new Handler<Timeout>() {
        @Override
        public void handle(Timeout timeout) {
            //logger.info("EPFD timeoutHandler called");
            if(!(Sets.intersection(suspected,alive).isEmpty())) {
                logger.info("increasing delta to : {}", period + delta);
                period = period + delta;
            }

            seqnum = seqnum + 1;

            //logger.info("Suspected size {} ", suspected.size());
            //logger.info("Alive size {} ", alive.size());
            for (NetAddress a : topology) {
                //logger.info("Looping node {}", a.toString());
                if(!alive.contains(a) && !suspected.contains(a)) {
                    //logger.info("Suspecting node {} adding it to suspected", a.toString());
                    suspected.add(a);
                    //trigger(new Suspect(a), epfd);
                }
                else if (alive.contains(a) && suspected.contains(a)) {
                    logger.info("Removing node {} from suspected", a.toString());
                    suspected.remove(a);

                    //trigger(new Restore(a), epfd);
                }
                trigger(new PL_Send(a, new HeartbeatRequest(seqnum)), perfectLink);
            }
            //logger.info("suspects: {}", suspected);
            trigger(new Suspects(suspected), epfd); // send suspects to overlay manager
            alive.clear();
            startTimer(period);
        }
    };

    protected final ClassMatchedHandler<HeartbeatRequest,PL_Deliver> hbRequestHandler = new ClassMatchedHandler<HeartbeatRequest, PL_Deliver>() {
        @Override
        public void handle(HeartbeatRequest heartbeatRequest, PL_Deliver message) {
            //logger.info("Received hbRequest from {} ", message.src);
            trigger(new PL_Send(message.src, new HeartbeatReply(heartbeatRequest.seq)), perfectLink);
        }
    };

    protected final ClassMatchedHandler<HeartbeatReply, PL_Deliver> hbReplyHandler = new ClassMatchedHandler<HeartbeatReply, PL_Deliver>() {
        @Override
        public void handle(HeartbeatReply heartbeatReply, PL_Deliver message) {
            //logger.info("Received hbReply from {} ", message.src);
            if(heartbeatReply.seq == seqnum || suspected.contains(message.src)) {
                logger.info("Adding {} to alive", message.src);
                alive.add(message.src);
            }
        }
    };
    {
        //subscribe(startHandler, control);
        subscribe(timeoutHandler, timer);
        subscribe(hbRequestHandler, perfectLink);
        subscribe(hbReplyHandler, perfectLink);
        subscribe(initHandler, boot);
    }
}
