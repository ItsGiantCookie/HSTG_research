package io.github.itsgiantcookie.hstglabs.grid;

import mekanism.api.Action;

/**
 * Capability on Data faces, data cables and data consumers. Amounts are abstract "data points".
 * Mirrors Mekanism's handler conventions: insert returns the remainder that was not accepted,
 * extract returns what was removed.
 * <p>
 * Data flows like Mekanism energy: producers (the research lab) push into adjacent handlers,
 * cables distribute their network buffer to every non-cable acceptor.
 */
public interface IDataHandler {

    long getStoredData();

    long getDataCapacity();

    long insertData(long amount, Action action);

    long extractData(long amount, Action action);
}
