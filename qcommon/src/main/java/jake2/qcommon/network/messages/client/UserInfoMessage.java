package jake2.qcommon.network.messages.client;

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
        sizebuf_t.WriteString(buffer, userInfo);
    }

    @Override
    public void parse(sizebuf_t buffer) {
        this.userInfo = sizebuf_t.ReadString(buffer);
    }

    @Override
    public int getSize() {
        return 1 + userInfo.length() + 1;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserInfoMessage that = (UserInfoMessage) o;
        return userInfo.equals(that.userInfo);
    }

    @Override
    public int hashCode() {
        return userInfo != null ? userInfo.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "UserInfoMessage{" +
                "userInfo='" + userInfo + '\'' +
                '}';
    }
}
