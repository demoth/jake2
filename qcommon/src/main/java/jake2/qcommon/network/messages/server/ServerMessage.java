package jake2.qcommon.network.messages.server;

import jake2.qcommon.MSG;
import jake2.qcommon.sizebuf_t;

public abstract class ServerMessage {
    public ServerMessageType type;

    public ServerMessage(ServerMessageType type) {
        this.type = type;
    }

    public final void writeTo(sizebuf_t buffer) {
        MSG.WriteByte(buffer, type.type);
        writeProperties(buffer);
    }

    protected abstract void writeProperties(sizebuf_t buffer);

    abstract void parse(sizebuf_t buffer);

    public static ServerMessage parseFromBuffer(ServerMessageType type, sizebuf_t buffer) {

        final ServerMessage msg;
        // skip parsing of messages not yet migrated to the NetworkMessage class
        switch (type) {
            case svc_bad:
            case svc_nop:
            case svc_spawnbaseline:
            case svc_download:
            case svc_deltapacketentities:
            case svc_packetentities:
            default:
                msg = null;
                break;
            case svc_muzzleflash:
                msg = new WeaponSoundMessage();
                break;
            case svc_muzzleflash2:
                msg = new MuzzleFlash2Message();
                break;
            case svc_layout:
                msg = new LayoutMessage();
                break;
            case svc_inventory:
                msg = new InventoryMessage();
                break;
            case svc_disconnect:
                msg = new DisconnectMessage();
                break;
            case svc_reconnect:
                msg = new ReconnectMessage();
                break;
            case svc_sound:
                msg = new SoundMessage();
                break;
            case svc_print:
                msg = new PrintMessage();
                break;
            case svc_stufftext:
                msg = new StuffTextMessage();
                break;
            case svc_serverdata:
                msg = new ServerDataMessage();
                break;
            case svc_configstring:
                msg = new ConfigStringMessage();
                break;
            case svc_centerprint:
                msg = new PrintCenterMessage();
                break;
            case svc_frame:
                msg = new FrameMessage();
                break;
            case svc_playerinfo:
                msg = new PlayerInfoMessage();
                break;
            case svc_temp_entity:
                int subtype = MSG.ReadByte(buffer);
                if (PointTEMessage.SUBTYPES.contains(subtype)) {
                    msg = new PointTEMessage(subtype);
                } else if (BeamTEMessage.SUBTYPES.contains(subtype)) {
                    msg = new BeamTEMessage(subtype);
                } else if (BeamOffsetTEMessage.SUBTYPES.contains(subtype)) {
                    msg = new BeamOffsetTEMessage(subtype);
                } else if (PointDirectionTEMessage.SUBTYPES.contains(subtype)) {
                    msg = new PointDirectionTEMessage(subtype);
                } else if (TrailTEMessage.SUBTYPES.contains(subtype)) {
                    msg = new TrailTEMessage(subtype);
                } else if (SplashTEMessage.SUBTYPES.contains(subtype)) {
                    msg = new SplashTEMessage(subtype);
                } else {
                    throw new IllegalStateException("Unexpected temp entity type:" + subtype);
                }
        }

        if (msg != null) {
            msg.parse(buffer);
        }
        return msg;
    }
}
