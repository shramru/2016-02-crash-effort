package mechanics;

import msgsystem.Address;

/**
 * Created by vladislav on 19.04.16.
 */
public interface GameMechanics {
    void registerUser(String userName, Address gameWebSocket);
    void unregisterUser(String userName);
    boolean isRegistered(String username);
    void onMessage(String username, String message);
}
