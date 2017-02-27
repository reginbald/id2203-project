package se.kth.id2203.simulation.epfd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.kth.id2203.bootstrapping.Bootstrapping;
import se.kth.id2203.epfd.AllNodes;
import se.kth.id2203.epfd.EventuallyPerfectFailureDetector;
import se.kth.id2203.epfd.Suspects;
import se.kth.id2203.network.BestEffortBroadcast;
import se.kth.id2203.networking.Message;
import se.kth.id2203.networking.NetAddress;
import se.kth.id2203.simulation.SimulationResultMap;
import se.kth.id2203.simulation.SimulationResultSingleton;
import se.sics.kompics.*;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.Timer;

import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

public class ScenarioEPFDClient extends ComponentDefinition {
    final static Logger LOG = LoggerFactory.getLogger(ScenarioEPFDClient.class);
    //******* Ports ******
    protected final Positive<Network> net = requires(Network.class);
    protected final Positive<Timer> timer = requires(Timer.class);
    protected final Positive<EventuallyPerfectFailureDetector> epfd = requires(EventuallyPerfectFailureDetector.class);

    protected final Positive<Bootstrapping> boot = requires(Bootstrapping.class);
    protected final Negative<Bootstrapping> boot2 = provides(Bootstrapping.class);

    //******* Fields ******
    private final NetAddress self = config().getValue("id2203.project.address", NetAddress.class);
    private final NetAddress server = config().getValue("id2203.project.bootstrap-address", NetAddress.class);
    private final SimulationResultMap res = SimulationResultSingleton.getInstance();
    private final Map<UUID, String> pending = new TreeMap<>();

    protected final Handler<Suspects> responseHandler = new Handler<Suspects>() {
        @Override
        public void handle(Suspects event) {
            LOG.debug("Got Suspects: {}", event);
            res.put(self.toString(), event.suspects.size());
        }
    };

    protected final ClassMatchedHandler<AllNodes, Message> nodesHandler = new ClassMatchedHandler<AllNodes, Message>() {

        @Override
        public void handle(AllNodes nodes, Message context) {
            LOG.debug("Got nodes: {}", nodes);
            trigger(nodes, boot2);
        }
    };

    {
        subscribe(nodesHandler, net);
        subscribe(responseHandler, boot);
    }
}
