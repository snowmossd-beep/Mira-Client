package mira.client.core.manager.client;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import mira.client.core.manager.IManager;
import mira.client.core.Managers;
import mira.client.features.modules.render.SoundFX;
import mira.client.features.modules.misc.ChatUtils;
import mira.client.utility.Timer;
import mira.client.utility.math.MathUtility;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import java.io.File;

import static mira.client.features.cmd.Command.sendMessage;
import static mira.client.core.manager.client.ConfigManager.SOUNDS_FOLDER;
import static mira.client.features.modules.render.ClientSettings.isRu;

public class SoundManager implements IManager {
    public final Identifier KEYPRESS_SOUND = Identifier.of("mira:keypress");
    public SoundEvent KEYPRESS_SOUNDEVENT = SoundEvent.of(KEYPRESS_SOUND);
    public final Identifier KEYRELEASE_SOUND = Identifier.of("mira:keyrelease");
    public SoundEvent KEYRELEASE_SOUNDEVENT = SoundEvent.of(KEYRELEASE_SOUND);
    public final Identifier ENABLE_SOUND = Identifier.of("mira:enable");
    public SoundEvent ENABLE_SOUNDEVENT = SoundEvent.of(ENABLE_SOUND);
    public final Identifier DISABLE_SOUND = Identifier.of("mira:disable");
    public SoundEvent DISABLE_SOUNDEVENT = SoundEvent.of(DISABLE_SOUND);
    public final Identifier SKEET_SOUND = Identifier.of("mira:skeet");
    public SoundEvent SKEET_SOUNDEVENT = SoundEvent.of(SKEET_SOUND);
    public final Identifier ORTHODOX_SOUND = Identifier.of("mira:orthodox");
    public SoundEvent ORTHODOX_SOUNDEVENT = SoundEvent.of(ORTHODOX_SOUND);
    public final Identifier BOOLEAN_SOUND = Identifier.of("mira:boolean");
    public SoundEvent BOOLEAN_SOUNDEVENT = SoundEvent.of(BOOLEAN_SOUND);
    public final Identifier SCROLL_SOUND = Identifier.of("mira:scroll");
    public SoundEvent SCROLL_SOUNDEVENT = SoundEvent.of(SCROLL_SOUND);
    public final Identifier SWIPEIN_SOUND = Identifier.of("mira:swipein");
    public SoundEvent SWIPEIN_SOUNDEVENT = SoundEvent.of(SWIPEIN_SOUND);
    public final Identifier SWIPEOUT_SOUND = Identifier.of("mira:swipeout");
    public SoundEvent SWIPEOUT_SOUNDEVENT = SoundEvent.of(SWIPEOUT_SOUND);
    public final Identifier PM_SOUND = Identifier.of("mira:pmsound");
    public SoundEvent PM_SOUNDEVENT = SoundEvent.of(PM_SOUND);
    public final Identifier RIFK_SOUND = Identifier.of("mira:rifk");
    public SoundEvent RIFK_SOUNDEVENT = SoundEvent.of(RIFK_SOUND);
    public final Identifier CUTIE_SOUND = Identifier.of("mira:cutie");
    public SoundEvent CUTIE_SOUNDEVENT = SoundEvent.of(CUTIE_SOUND);
    public final Identifier WITHER_SOUND = Identifier.of("mira:wither");
    public SoundEvent WITHER_SOUNDEVENT = SoundEvent.of(WITHER_SOUND);

    private final Timer scrollTimer = new Timer();

    public void registerSounds() {
        Registry.register(Registries.SOUND_EVENT, KEYPRESS_SOUND, KEYPRESS_SOUNDEVENT);
        Registry.register(Registries.SOUND_EVENT, KEYRELEASE_SOUND, KEYRELEASE_SOUNDEVENT);
        Registry.register(Registries.SOUND_EVENT, ENABLE_SOUND, ENABLE_SOUNDEVENT);
        Registry.register(Registries.SOUND_EVENT, DISABLE_SOUND, DISABLE_SOUNDEVENT);
        Registry.register(Registries.SOUND_EVENT, SKEET_SOUND, SKEET_SOUNDEVENT);
        Registry.register(Registries.SOUND_EVENT, ORTHODOX_SOUND, ORTHODOX_SOUNDEVENT);
        Registry.register(Registries.SOUND_EVENT, SCROLL_SOUND, SCROLL_SOUNDEVENT);
        Registry.register(Registries.SOUND_EVENT, BOOLEAN_SOUND, BOOLEAN_SOUNDEVENT);
        Registry.register(Registries.SOUND_EVENT, SWIPEIN_SOUND, SWIPEIN_SOUNDEVENT);
        Registry.register(Registries.SOUND_EVENT, SWIPEOUT_SOUND, SWIPEOUT_SOUNDEVENT);
        Registry.register(Registries.SOUND_EVENT, PM_SOUND, PM_SOUNDEVENT);
        Registry.register(Registries.SOUND_EVENT, RIFK_SOUND, RIFK_SOUNDEVENT);
        Registry.register(Registries.SOUND_EVENT, CUTIE_SOUND, CUTIE_SOUNDEVENT);
        Registry.register(Registries.SOUND_EVENT, WITHER_SOUND, WITHER_SOUNDEVENT);
    }

    public void playHitSound(SoundFX.HitSound value) {
        switch (value) {
            case SKEET -> playSound(SKEET_SOUNDEVENT);
            case KEYBOARD -> playSound(KEYPRESS_SOUNDEVENT);
            case CUTIE -> playSound(CUTIE_SOUNDEVENT);
            case RIFK -> playSound(RIFK_SOUNDEVENT);
            case CUSTOM -> playSound("hit");
        }
    }

    public void playEnable() {
        if (ModuleManager.soundFX.enableMode.getValue() == SoundFX.OnOffSound.Inertia) {
            playSound(ENABLE_SOUNDEVENT);
        } else if (ModuleManager.soundFX.enableMode.getValue() == SoundFX.OnOffSound.Custom) {
            playSound("enable");
        }
    }

    public void playDisable() {
        if (ModuleManager.soundFX.disableMode.getValue() == SoundFX.OnOffSound.Inertia) {
            playSound(DISABLE_SOUNDEVENT);
        } else if (ModuleManager.soundFX.disableMode.getValue() == SoundFX.OnOffSound.Custom) {
            playSound("disable");
        }
    }

    public void playScroll() {
        if (scrollTimer.every(50)) {
            if (ModuleManager.soundFX.scrollSound.getValue() == SoundFX.ScrollSound.KeyBoard) {
                playSound(KEYPRESS_SOUNDEVENT);
            } else if (ModuleManager.soundFX.scrollSound.getValue() == SoundFX.ScrollSound.Custom) {
                playSound("scroll");
            }
        }
    }

    public void playSound(SoundEvent sound) {
        if (mc.player != null && mc.world != null)
            mc.world.playSound(mc.player, mc.player.getBlockPos(), sound, SoundCategory.BLOCKS, (float) ModuleManager.soundFX.volume.getValue() / 100f, 1f);
    }

    public void playSound(String name) {
        try {
            Clip clip = AudioSystem.getClip();
            clip.open(AudioSystem.getAudioInputStream(new File(SOUNDS_FOLDER, name + ".wav").getAbsoluteFile()));
            FloatControl floatControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
            floatControl.setValue((floatControl.getMaximum() - floatControl.getMinimum() * ((float) ModuleManager.soundFX.volume.getValue() / 100f)) + floatControl.getMinimum());
            clip.start();
        } catch (Exception e) {
            sendMessage((isRu() ? "Ошибка воспроизведения звука! Проверь " : "Error with playing sound! Check ") + new File(SOUNDS_FOLDER, name + ".wav").getAbsolutePath());
        }
    }

    public void playSlider() {
        playSound(SCROLL_SOUNDEVENT);
    }

    public void playBoolean() {
        playSound(BOOLEAN_SOUNDEVENT);
    }

    public void playWither() {
        playSound(WITHER_SOUNDEVENT);
    }

    public void playWither(float volume) {
        if (mc.player == null || mc.world == null) return;
        mc.getSoundManager().play(
            net.minecraft.client.sound.PositionedSoundInstance.ambient(
                WITHER_SOUNDEVENT, volume, 1.0f
            )
        );
    }

    public void playSwipeIn() {
        playSound(SWIPEIN_SOUNDEVENT);
    }

    public void playSwipeOut() {
        playSound(SWIPEOUT_SOUNDEVENT);
    }

    public void playPmSound(ChatUtils.PMSound sound) {
        if (sound == ChatUtils.PMSound.Default) playSound(PM_SOUNDEVENT);
        else Managers.SOUND.playSound("pmsound");
    }
    }
