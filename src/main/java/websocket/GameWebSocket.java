package websocket;

import main.AccountService;
import main.UserProfile;
import msgsystem.*;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;

import java.io.IOException;

/**
 * Created by vladislav on 19.04.16.
 */
@WebSocket
public class GameWebSocket implements Subscriber {
    private final AccountService accountService;
    private final String sessionId;
    private String username;
    private Session currentSession;

    private final MessageSystem messageSystem;
    private Address address;
    private final Address addressGM;

    private boolean connecting;

    public GameWebSocket(String sessionId, AccountService accountService, MessageSystem messageSystem, Address addressGM) {
        this.accountService = accountService;
        this.messageSystem = messageSystem;
        this.sessionId = sessionId;
        this.addressGM = addressGM;
        connecting = true;
    }

    @SuppressWarnings("unused")
    @OnWebSocketMessage
    public void onMessage(Session session, String data) {
        if (username == null) return;

        final MsgBase msgData = new MsgInData(address, addressGM, username, data);
        messageSystem.sendMessage(msgData);
    }

    @SuppressWarnings("unused")
    @OnWebSocketConnect
    public void onConnect(Session session) {
        address = new Address();
        messageSystem.register(address);
        connecting = false;

        currentSession = session;
        final UserProfile user = accountService.getUserBySession(sessionId);
        if (user == null) {
            session.close(Response.SC_FORBIDDEN, "Your access to this resource is denied");
            return;
        }
        username = user.getLogin();

        final MsgBase messageRegister = new MsgRegister(address, addressGM, username);
        messageSystem.sendMessage(messageRegister);
    }

    @SuppressWarnings("unused")
    @OnWebSocketClose
    public void onDisconnect(int statusCode, String reason) {
        System.out.println("User disconnected with code " + statusCode + " by reason: " + reason);
        if (username != null) {
            final MsgBase messageUnregister = new MsgUnregister(address, addressGM, username);
            messageSystem.sendMessage(messageUnregister);
        }
    }

    public void sendMessage(String message) {
        try {
            currentSession.getRemote().sendString(message);
        } catch (IOException e) {
            System.out.println("WebSocket error: " + e.getMessage());
        }
    }

    public boolean isConnecting() {
        return connecting;
    }

    public boolean isOpen() {
        return currentSession != null && currentSession.isOpen();
    }

    public void close(int statusCode, String reason) {
        currentSession.close(statusCode, reason);
    }

    @Override
    public Address getAddress() {
        return address;
    }

    @Override
    public MessageSystem getMessageSystem() {
        return messageSystem;
    }

}
