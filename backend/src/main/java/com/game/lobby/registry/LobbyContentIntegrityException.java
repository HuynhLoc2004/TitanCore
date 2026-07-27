package com.game.lobby.registry;

public class LobbyContentIntegrityException extends RuntimeException {

    public LobbyContentIntegrityException() {
        super("Lobby content integrity invariant failed");
    }
}
