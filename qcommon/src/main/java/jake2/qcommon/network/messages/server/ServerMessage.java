package jake2.qcommon.network.messages.server;

import jake2.qcommon.Defines;
import jake2.qcommon.MSG;
import jake2.qcommon.entity_state_t;
import jake2.qcommon.sizebuf_t;
import jake2.qcommon.util.Math3D;

public abstract class ServerMessage {
    public ServerMessageType type;

    public ServerMessage(ServerMessageType type) {
        this.type = type;
    }

    /*
     * ================= CL_ParseEntityBits
     *
     * Returns the entity number and the header bits =================
     */
    @Deprecated
    public static int ParseEntityBits(int bits[], sizebuf_t buffer) {

        int total = MSG.ReadByte(buffer);
        int b;
        if ((total & Defines.U_MOREBITS1) != 0) {

            b = MSG.ReadByte(buffer);
            total |= b << 8;
        }
        if ((total & Defines.U_MOREBITS2) != 0) {

            b = MSG.ReadByte(buffer);
            total |= b << 16;
        }
        if ((total & Defines.U_MOREBITS3) != 0) {

            b = MSG.ReadByte(buffer);
            total |= b << 24;
        }

        int number;
        if ((total & Defines.U_NUMBER16) != 0)
            number = MSG.ReadShort(buffer);
        else
            number = MSG.ReadByte(buffer);

        bits[0] = total;

        return number;
    }

    /*
     * ================== CL_ParseDelta
     *
     * Can go from either a baseline or a previous packet_entity
     * ==================
     */
    public static void ParseDelta(entity_state_t from, entity_state_t to, int number, int bits, sizebuf_t buffer) {
        // set everything to the state we are delta'ing from
        to.set(from);

        Math3D.VectorCopy(from.origin, to.old_origin);
        to.number = number;

        if ((bits & Defines.U_MODEL) != 0)
            to.modelindex = MSG.ReadByte(buffer);
        if ((bits & Defines.U_MODEL2) != 0)
            to.modelindex2 = MSG.ReadByte(buffer);
        if ((bits & Defines.U_MODEL3) != 0)
            to.modelindex3 = MSG.ReadByte(buffer);
        if ((bits & Defines.U_MODEL4) != 0)
            to.modelindex4 = MSG.ReadByte(buffer);

        if ((bits & Defines.U_FRAME8) != 0)
            to.frame = MSG.ReadByte(buffer);
        if ((bits & Defines.U_FRAME16) != 0)
            to.frame = MSG.ReadShort(buffer);

        // used for laser colors
        if ((bits & Defines.U_SKIN8) != 0 && (bits & Defines.U_SKIN16) != 0)
            to.skinnum = MSG.ReadLong(buffer);
        else if ((bits & Defines.U_SKIN8) != 0)
            to.skinnum = MSG.ReadByte(buffer);
        else if ((bits & Defines.U_SKIN16) != 0)
            to.skinnum = MSG.ReadShort(buffer);

        if ((bits & (Defines.U_EFFECTS8 | Defines.U_EFFECTS16)) == (Defines.U_EFFECTS8 | Defines.U_EFFECTS16))
            to.effects = MSG.ReadLong(buffer);
        else if ((bits & Defines.U_EFFECTS8) != 0)
            to.effects = MSG.ReadByte(buffer);
        else if ((bits & Defines.U_EFFECTS16) != 0)
            to.effects = MSG.ReadShort(buffer);

        if ((bits & (Defines.U_RENDERFX8 | Defines.U_RENDERFX16)) == (Defines.U_RENDERFX8 | Defines.U_RENDERFX16))
            to.renderfx = MSG.ReadLong(buffer);
        else if ((bits & Defines.U_RENDERFX8) != 0)
            to.renderfx = MSG.ReadByte(buffer);
        else if ((bits & Defines.U_RENDERFX16) != 0)
            to.renderfx = MSG.ReadShort(buffer);

        if ((bits & Defines.U_ORIGIN1) != 0)
            to.origin[0] = MSG.ReadCoord(buffer);
        if ((bits & Defines.U_ORIGIN2) != 0)
            to.origin[1] = MSG.ReadCoord(buffer);
        if ((bits & Defines.U_ORIGIN3) != 0)
            to.origin[2] = MSG.ReadCoord(buffer);

        if ((bits & Defines.U_ANGLE1) != 0)
            to.angles[0] = MSG.ReadAngle(buffer);
        if ((bits & Defines.U_ANGLE2) != 0)
            to.angles[1] = MSG.ReadAngle(buffer);
        if ((bits & Defines.U_ANGLE3) != 0)
            to.angles[2] = MSG.ReadAngle(buffer);

        if ((bits & Defines.U_OLDORIGIN) != 0)
            MSG.ReadPos(buffer, to.old_origin);

        if ((bits & Defines.U_SOUND) != 0)
            to.sound = MSG.ReadByte(buffer);

        if ((bits & Defines.U_EVENT) != 0)
            to.event = MSG.ReadByte(buffer);
        else
            to.event = 0;

        if ((bits & Defines.U_SOLID) != 0)
            to.solid = MSG.ReadShort(buffer);
    }

    public final void writeTo(sizebuf_t buffer) {
        MSG.WriteByte(buffer, type.type);
        writeProperties(buffer);
    }

    protected abstract void writeProperties(sizebuf_t buffer);

    abstract void parse(sizebuf_t buffer);

    public static ServerMessage parseFromBuffer(ServerMessageType type, sizebuf_t buffer) {

        final ServerMessage msg;
        // skip parsing of messages not yet migrated to the ServerMessage class
        switch (type) {
            case svc_bad:
            case svc_nop:
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
            case svc_spawnbaseline:
                msg = new SpawnBaselineMessage();
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
