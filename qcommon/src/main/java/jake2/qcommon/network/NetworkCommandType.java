package jake2.qcommon.network;

// todo: rename to NetworkMessageType
// todo: enum
public interface NetworkCommandType {
    // these ops are known to the game dll
    // protocol bytes that can be directly added to messages
    int svc_bad = 0;
    int svc_muzzleflash = 1;
    int svc_muzzleflash2 = 2;
    int svc_temp_entity = 3;
    int svc_layout = 4;
    // +
    int svc_inventory = 5;

    // the rest are private to the client and server
    // +
    int svc_nop = 6;
    // +
    int svc_disconnect = 7;
    // +
    int svc_reconnect = 8;
    // +
    int svc_sound = 9; // <see code>
    // +
    int svc_print = 10; // [byte] id [string] null terminated string
    // +
    int svc_stufftext = 11;
    // [string] stuffed into client's console buffer, should be \n terminated
    int svc_serverdata = 12; // [long] protocol ...
    int svc_configstring = 13; // [short] [string]
    int svc_spawnbaseline = 14;
    int svc_centerprint = 15; // [string] to put in center of the screen
    int svc_download = 16; // [short] size [size bytes]
    int svc_playerinfo = 17; // variable
    int svc_packetentities = 18; // [...]
    int svc_deltapacketentities = 19; // [...]
    int svc_frame = 20;
}
