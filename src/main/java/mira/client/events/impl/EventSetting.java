package mira.client.events.impl;

import mira.client.events.Event;
import mira.client.setting.Setting;

public class EventSetting extends Event {
    final Setting<?> setting;

    public EventSetting(Setting<?> setting){
        this.setting = setting;
    }

    public Setting<?> getSetting() {
        return setting;
    }
}
