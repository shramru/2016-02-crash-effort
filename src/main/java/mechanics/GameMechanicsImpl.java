package mechanics;

import main.FileHelper;
import msgsystem.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.util.*;

/**
 * Created by vladislav on 19.04.16.
 */
public class GameMechanicsImpl implements GameMechanics, Subscriber, Runnable {

    private static final String CARDS = "cfg/cards.json";
    private JSONArray cards;

    private final Map<String, Lobby> userToLobby = new HashMap<>();
    private final Map<String, Address> userToSocketAddress = new HashMap<>();
    Lobby vacantLobby;

    final MessageSystem messageSystem;
    final Address address;

    public GameMechanicsImpl(MessageSystem messageSystem) throws IOException {
        this.messageSystem = messageSystem;
        address = new Address();
        cards = new JSONArray(FileHelper.readAllText(CARDS));
    }

    @Override
    public void registerUser(String userName, Address addressSocket) {
        userToSocketAddress.put(userName, addressSocket);
        if (vacantLobby == null) {
            vacantLobby = new Lobby(new GameUser(userName));
        } else {
            vacantLobby.setSecondUser(new GameUser(userName));
            userToLobby.put(vacantLobby.getFirstUser().getUsername(), vacantLobby);
            userToLobby.put(vacantLobby.getSecondUser().getUsername(), vacantLobby);

            sendStartGame();
            vacantLobby = null;
        }
    }

    @Override
    public void unregisterUser(String userName) {
        final Lobby lobby = userToLobby.get(userName);
        if (lobby != null) {
            final GameUser gameUser = lobby.getUserbyName(userName);
            if (gameUser != null) {
                gameUser.disconnect();
            }
            final GameUser enemy = lobby.getEnemybyName(userName);

            if (enemy != null && enemy.isConnected()) {
                final MsgBase msgEnemyDisconnected = new MsgEnemyDisconnected(address, userToSocketAddress.get(enemy.getUsername()));
                messageSystem.sendMessage(msgEnemyDisconnected);
            }
        }

        userToSocketAddress.remove(userName);
        userToLobby.remove(userName);
        if (vacantLobby != null && vacantLobby.getUserbyName(userName) != null)
            vacantLobby = null;
    }

    @Override
    public boolean isRegistered(String username) {
        return userToSocketAddress.containsKey(username);
    }

    @Override
    public void onMessage(String username, JSONObject data) {
        final Lobby lobby = userToLobby.get(username);
        final String command = data.getString("command");
        switch (command) {
            case "nextTurn":
                nextTurn(lobby, data.getJSONArray("cards"));
                break;
            case "nextRound":
                nextRound(lobby, username);
                break;
            default:
                break;
        }
    }

    private void sendStartGame() {
        final int cardsCount = 3;

        final MsgBase msgStartGameFirst = new MsgStartGame(address, userToSocketAddress.get(vacantLobby.getFirstUser().getUsername()),
                true, vacantLobby.getFirstUser().getHealth(), vacantLobby.getSecondUser().getHealth(), getRandomCards(cardsCount));
        messageSystem.sendMessage(msgStartGameFirst);
        final MsgBase msgStartGameSecond = new MsgStartGame(address, userToSocketAddress.get(vacantLobby.getSecondUser().getUsername()),
                false, vacantLobby.getSecondUser().getHealth(), vacantLobby.getFirstUser().getHealth(), getRandomCards(cardsCount));
        messageSystem.sendMessage(msgStartGameSecond);
    }

    public JSONArray getRandomCards(int count) {
        final JSONArray jsonArray = new JSONArray();
        final Random r = new Random();
        final List<Integer> ids = new ArrayList<>(count);

        for (int i = 0; i < count; ++i) {
            final int id = r.nextInt(cards.length());
            if (!ids.contains(id)) {
                ids.add(id);
                jsonArray.put(cards.get(id));
            } else { --i; }
        }
        return jsonArray;
    }

    private void nextTurn(Lobby lobby, JSONArray inCards) {
        setScore(lobby, inCards);

        if (lobby.isFirstUserTurn()) {

            final MsgBase msgNextTurnFirst = new MsgNextTurn(address, userToSocketAddress.get(lobby.getFirstUser().getUsername()),
                    false, -1);
            messageSystem.sendMessage(msgNextTurnFirst);
            final MsgBase msgNextTurnSecond = new MsgNextTurn(address, userToSocketAddress.get(lobby.getSecondUser().getUsername()),
                    true, inCards.length());
            messageSystem.sendMessage(msgNextTurnSecond);

        } else if (lobby.isSecondUserTurn()) {
            final boolean endGame = endRound(lobby);
            if (!endGame) {
                final MsgBase msgEndRoundFirst = new MsgEndRound(address, userToSocketAddress.get(lobby.getFirstUser().getUsername()),
                        lobby.getFirstUser().getHealth(), lobby.getSecondUser().getHealth(), lobby.getFirstUser().getRoundScores(),
                        lobby.getSecondUser().getRoundScores(), lobby.getSecondUser().getRoundCards());
                messageSystem.sendMessage(msgEndRoundFirst);
                final MsgBase msgEndRoundSecond = new MsgEndRound(address, userToSocketAddress.get(lobby.getSecondUser().getUsername()),
                        lobby.getSecondUser().getHealth(), lobby.getFirstUser().getHealth(), lobby.getSecondUser().getRoundScores(),
                        lobby.getFirstUser().getRoundScores(), lobby.getFirstUser().getRoundCards());
                messageSystem.sendMessage(msgEndRoundSecond);
                lobby.setAllWaiting();
            }
        }
        lobby.nextTurn();
    }

    private void setScore(Lobby lobby, JSONArray inCards) {
        final GameUser gameUser = lobby.getCurrentUser();
        gameUser.setRoundScores(0);
        gameUser.setRoundCards(idToCards(inCards));
        for (int i = 0; i < inCards.length(); ++i) {
            final JSONObject card = cards.getJSONObject(inCards.getInt(i) - 1);
            gameUser.setRoundScores(gameUser.getRoundScores() + card.getInt("power"));
        }
    }

    private JSONArray idToCards(JSONArray ids) {
        final JSONArray cardsArray = new JSONArray();
        for (int i = 0; i < ids.length(); ++i) {
            cardsArray.put(cards.getJSONObject(ids.getInt(i) - 1));
        }
        return cardsArray;
    }

    private boolean endRound(Lobby lobby) {
        final int score = lobby.getFirstUser().getRoundScores() - lobby.getSecondUser().getRoundScores();

        if (score > 0) {
            lobby.getSecondUser().setHealth(lobby.getSecondUser().getHealth() - score);
            if (lobby.getSecondUser().getHealth() < 1) {
                sendWin(lobby.getFirstUser());
                sendLose(lobby.getSecondUser());
                return true;
            }
        } else {
            lobby.getFirstUser().setHealth(lobby.getFirstUser().getHealth() + score);
            if (lobby.getFirstUser().getHealth() < 1) {
                sendWin(lobby.getSecondUser());
                sendLose(lobby.getFirstUser());
                return true;
            }
        }
        return false;
    }

    private void nextRound(Lobby lobby, String username) {
        final GameUser gameUser = lobby.getUserbyName(username);
        if (gameUser == null) return;

        gameUser.setWaiting(false);
        final boolean firstWait = lobby.getFirstUser().isWaiting();
        final boolean secondWait = lobby.getSecondUser().isWaiting();

        if (!firstWait && !secondWait)
            sendNextRound(lobby);
    }

    private void sendNextRound(Lobby lobby) {
        final MsgBase msgNextRoundFirst = new MsgNextRound(address, userToSocketAddress.get(lobby.getFirstUser().getUsername()), true,
                getRandomCards(lobby.getFirstUser().getRoundCards().length()));
        messageSystem.sendMessage(msgNextRoundFirst);
        final MsgBase msgNextRoundSecond = new MsgNextRound(address, userToSocketAddress.get(lobby.getSecondUser().getUsername()), false,
                getRandomCards(lobby.getSecondUser().getRoundCards().length()));
        messageSystem.sendMessage(msgNextRoundSecond);
    }

    private void sendWin(GameUser user) {
        final MsgBase msgEndGame = new MsgEndGame(address, userToSocketAddress.get(user.getUsername()), true);
        messageSystem.sendMessage(msgEndGame);
        disconnect(user.getUsername());
    }

    private void sendLose(GameUser user) {
        final MsgBase msgEndGame = new MsgEndGame(address, userToSocketAddress.get(user.getUsername()), false);
        messageSystem.sendMessage(msgEndGame);
        disconnect(user.getUsername());
    }

    private void disconnect(String username) {
        final MsgBase msgDisconnect = new MsgDisconnect(address, userToSocketAddress.get(username));
        messageSystem.sendMessage(msgDisconnect);
    }

    @Override
    public Address getAddress() {
        return address;
    }

    @Override
    public MessageSystem getMessageSystem() {
        return messageSystem;
    }

    public void start() {
        (new Thread(this)).start();
    }

    @SuppressWarnings("OverlyBroadCatchBlock")
    @Override
    public void run() {
        while (!Thread.interrupted()) {
            try {
                messageSystem.execForSubscriber(this);
                Thread.sleep(MessageSystem.IDLE_TIME);
            } catch (InterruptedException e) {
                return;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
