package com.skillforge.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.skillforge.SkillForge;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/**
 * ฝั่ง client: ปุ่ม Z X C V ใช้สกิลช่อง 1-4 และปุ่ม G เปิดเมนูสร้างสกิล
 * ทุกอย่างส่งเป็นคำสั่ง /skill ไปให้เซิร์ฟเวอร์ ไม่ต้องมี packet แบบกำหนดเอง
 */
public class SkillForgeClient implements ClientModInitializer {
    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(SkillForge.MOD_ID, "main"));

    private static final KeyMapping[] CAST = new KeyMapping[4];
    private static KeyMapping open;

    @Override
    public void onInitializeClient() {
        int[] keys = {InputConstants.KEY_Z, InputConstants.KEY_X, InputConstants.KEY_C, InputConstants.KEY_V};
        for (int i = 0; i < CAST.length; i++) {
            CAST[i] = new KeyMapping("key.skillforge.cast" + (i + 1), keys[i], CATEGORY);
            KeyMappingHelper.registerKeyMapping(CAST[i]);
        }
        open = new KeyMapping("key.skillforge.open", InputConstants.KEY_G, CATEGORY);
        KeyMappingHelper.registerKeyMapping(open);

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.player == null) return;
            for (int i = 0; i < CAST.length; i++) {
                while (CAST[i].consumeClick()) {
                    mc.player.connection.sendCommand("skill cast " + (i + 1));
                }
            }
            while (open.consumeClick()) {
                mc.setScreenAndShow(new SkillBuilderScreen());
            }
        });
    }
}
