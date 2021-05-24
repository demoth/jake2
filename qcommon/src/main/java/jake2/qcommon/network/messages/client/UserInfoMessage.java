package jake2.qcommon.network.messages.client;

import jake2.qcommon.MSG;
import jake2.qcommon.sizebuf_t;

public class UserInfoMessage extends ClientMessage {

    public String userInfo;

    public UserInfoMessage(String userInfo) {
        super(ClientMessageType.CLC_USERINFO);
        this.userInfo = userInfo;
    }

    @Override
    protected void writeProperties(sizebuf_t buffer) {
        MSG.WriteString(buffer, userInfo);
    }

    @Override
    void parse(sizebuf_t buffer) {

    }
}
