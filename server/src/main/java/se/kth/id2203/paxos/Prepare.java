package se.kth.id2203.paxos;

import com.google.common.base.MoreObjects;
import se.sics.kompics.KompicsEvent;

import java.io.Serializable;

public class Prepare implements KompicsEvent, Serializable {
    private static final long serialVersionUID = 115L;

    public final int ts;
    public final int l;
    public final int tPrime;

    public Prepare(int ts, int l, int tPrime) {
        this.ts = ts;
        this.l = l;
        this.tPrime = tPrime;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("ptsPrime", ts)
                .add("l", l)
                .add("tPrime", tPrime)
                .toString();
    }
}
