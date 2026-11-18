package com.skillforge;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SkillForge implements ModInitializer {
    public static final String MOD_ID = "skillforge";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        SkillAttachment.init();
        SkillCommands.register();
        ServerTickEvents.END_SERVER_TICK.register(server -> SkillCaster.tick());
        LOGGER.info("Skill Forge loaded");
    }
}
