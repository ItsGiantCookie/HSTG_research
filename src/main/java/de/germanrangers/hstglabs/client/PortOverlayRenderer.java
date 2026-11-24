package de.germanrangers.hstglabs.client;

import de.germanrangers.hstglabs.HstgLabs;
import de.germanrangers.hstglabs.lab.ResearchLabBlockEntity;
import de.germanrangers.hstglabs.mek.MekanismCompat;
import de.germanrangers.hstglabs.mek.config.PortType;
import de.germanrangers.hstglabs.mek.config.TransmissionKind;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;

/**
 * Mirrors Mekanism's side-config view: while the player holds Mekanism's Configurator in a configurate
 * mode, the face they look at shows that transmission kind's overlay, tinted with the face's port-type
 * color at 60% alpha (gray for none), like Mekanism's RenderTickHandler.
 */
@EventBusSubscriber(modid = HstgLabs.MODID, value = Dist.CLIENT)
public final class PortOverlayRenderer {

    private static final float ALPHA = 0.6F;
    private static final float OFFSET = 0.002F;
    // Vertex order per face (vanilla FaceInfo): top-left, bottom-left, bottom-right, top-right.
    private static final float[][][] CORNERS = {
          {{0, 0, 1}, {0, 0, 0}, {1, 0, 0}, {1, 0, 1}}, // DOWN
          {{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}}, // UP
          {{1, 1, 0}, {1, 0, 0}, {0, 0, 0}, {0, 1, 0}}, // NORTH
          {{0, 1, 1}, {0, 0, 1}, {1, 0, 1}, {1, 1, 1}}, // SOUTH
          {{0, 1, 0}, {0, 0, 0}, {0, 0, 1}, {0, 1, 1}}, // WEST
          {{1, 1, 1}, {1, 0, 1}, {1, 0, 0}, {1, 1, 0}}, // EAST
    };

    private PortOverlayRenderer() {
    }

    @SubscribeEvent
    public static void onBlockHighlight(RenderHighlightEvent.Block event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        TransmissionKind kind = MekanismCompat.configuratorKind(MekanismCompat.heldConfigurator(mc.player));
        if (kind == null) {
            return; // no configurator, or not in a configurate mode
        }
        BlockHitResult hit = event.getTarget();
        BlockPos pos = hit.getBlockPos();
        if (!(mc.level.getBlockEntity(pos) instanceof ResearchLabBlockEntity lab)) {
            return;
        }
        Direction face = hit.getDirection();
        PortType type = lab.getPort(kind, face);
        TextureAtlasSprite sprite = mc.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(kind.overlay());
        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
        VertexConsumer buffer = event.getMultiBufferSource().getBuffer(Sheets.translucentCullBlockSheet());
        drawFace(poseStack.last(), buffer, face, sprite, type.argb(ALPHA));
        poseStack.popPose();
    }

    private static void drawFace(PoseStack.Pose pose, VertexConsumer buffer, Direction face, TextureAtlasSprite sprite, int argb) {
        float nx = face.getStepX();
        float ny = face.getStepY();
        float nz = face.getStepZ();
        float[][] corners = CORNERS[face.get3DDataValue()];
        for (int i = 0; i < 4; i++) {
            float[] c = corners[i];
            float u = i < 2 ? sprite.getU0() : sprite.getU1();
            float v = i == 0 || i == 3 ? sprite.getV0() : sprite.getV1();
            buffer.addVertex(pose, c[0] + nx * OFFSET, c[1] + ny * OFFSET, c[2] + nz * OFFSET)
                  .setColor(argb)
                  .setUv(u, v)
                  .setOverlay(OverlayTexture.NO_OVERLAY)
                  .setLight(LightTexture.FULL_BRIGHT)
                  .setNormal(pose, nx, ny, nz);
        }
    }
}
