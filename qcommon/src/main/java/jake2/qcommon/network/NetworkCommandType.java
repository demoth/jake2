package jake2.qcommon.network;

// todo: rename to NetworkMessageType
public enum NetworkCommandType {
    // these ops are known to the game dll
    // protocol bytes that can be directly added to messages
    svc_bad(0),
    // +
    svc_muzzleflash(1),
    // +
    svc_muzzleflash2(2),
    // +
    svc_temp_entity(3),
    // +
    svc_layout(4),
    // +
    svc_inventory(5),

    // the rest are private to the client and server
    // +
    svc_nop(6),
    // +
    svc_disconnect(7),
    // +
    svc_reconnect(8),
    // +
    svc_sound(9), // <see code>
    // +
    svc_print(10), // [byte] id [string] null terminated string
    // +
    svc_stufftext(11),
    // [string] stuffed into client's console buffer, should be \n terminated
    // +
    svc_serverdata(12), // [long] protocol ...
    // +
    svc_configstring(13), // [short] [string]
    svc_spawnbaseline(14),
    // +
    svc_centerprint(15), // [string] to put in center of the screen
    // skip
    svc_download(16), // [short] size [size bytes]
    // +
    svc_playerinfo(17), // variable
    svc_packetentities(18), // [...]
    // +
    svc_deltapacketentities(19), // [...]
    // +
    svc_frame(20);

    NetworkCommandType(int type) {
        this.type = type;
    }

    public static NetworkCommandType fromInt(int type) {
        for (NetworkCommandType t: values()) {
            if (t.type == type)
                return t;
        }
        return svc_nop;
    }

    public final int type;

}
