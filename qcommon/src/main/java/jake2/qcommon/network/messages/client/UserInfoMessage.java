package jake2.qcommon.network.messages.client;

import jake2.qcommon.MSG;
import jake2.qcommon.sizebuf_t;

public class UserInfoMessage extends ClientMessage {

    public String userInfo;

    public UserInfoMessage() {
        super(ClientMessageType.CLC_USERINFO);
    }

    public UserInfoMessage(String userInfo) {
        this();
        this.userInfo = userInfo;
    }

    @Override
    protected void writeProperties(sizebuf_t buffer) {
        MSG.WriteString(buffer, userInfo);
    }

    @Override
    void parse(sizebuf_t buffer) {
        this.userInfo = MSG.ReadString(buffer);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserInfoMessage that = (UserInfoMessage) o;
        return userInfo.equals(that.userInfo);
    }
}
