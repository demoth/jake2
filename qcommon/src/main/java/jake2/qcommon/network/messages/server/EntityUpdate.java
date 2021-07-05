package jake2.qcommon.network.messages.server;

import jake2.qcommon.entity_state_t;

/**
 * This class represents an update of a certain entity, calculated and transmitted from server to client on each frame.
 */
public class EntityUpdate {
    public final DeltaEntityHeader header;
    public final entity_state_t oldState;
    public final entity_state_t newState;
    public final boolean force;
    public final boolean isNewEntity;

    // send an entity state change (delta of new & old states)
    public EntityUpdate(entity_state_t oldState, entity_state_t newState, boolean force, boolean isNewEntity) {
        this.oldState = oldState;
        this.newState = newState;
        this.force = force;
        this.isNewEntity = isNewEntity;
        // not required, will be calculated based on above parameters
        this.header = null;
    }

    // send an entity removed notification in the header (former entity bits)
    public EntityUpdate(DeltaEntityHeader header) {
        this.header = header;

        // not required
        this.oldState = null;
        this.newState = null;
        this.force = false;
        this.isNewEntity = false;
    }

    // Receive the update from the server.
    public EntityUpdate(DeltaEntityHeader header, entity_state_t newState) {
        this.header = header;
        this.newState = newState;

        // not required
        this.oldState = null;
        this.force = false;
        this.isNewEntity = false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        EntityUpdate that = (EntityUpdate) o;

        if (header != null ? !header.equals(that.header) : that.header != null) return false;
        return newState != null ? newState.equals(that.newState) : that.newState == null;
    }

    @Override
    public int hashCode() {
        int result = header != null ? header.hashCode() : 0;
        result = 31 * result + (newState != null ? newState.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "EntityUpdate{" +
                "header=" + header +
                ", newState=" + newState +
                '}';
    }
}
