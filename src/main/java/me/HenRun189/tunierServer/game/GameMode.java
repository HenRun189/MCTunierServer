package me.HenRun189.tunierServer.game;

import org.bukkit.event.Event;

public interface GameMode {

    void start();

    void stop();

    void handleEvent(Event event);
}