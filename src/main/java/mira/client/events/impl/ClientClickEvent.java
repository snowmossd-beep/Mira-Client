package mira.client.events.impl;

import net.minecraft.text.ClickEvent;

public class ClientClickEvent extends ClickEvent {
    public ClientClickEvent(Action action, String value) {
        super(action, value);
    }
}
