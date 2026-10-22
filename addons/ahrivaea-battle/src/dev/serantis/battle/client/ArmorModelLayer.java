package dev.serantis.battle.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.ahrivaea.client.GeoModels;
import dev.serantis.battle.InfluenceArmor;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws the 3D influence armour, Winged Boots and Teleporting Boots on players (their equipment assets have no vanilla
 * layers, so nothing else is drawn for them). Each worn piece is its own model (assets/ahrivaea/geo/creature/
 * serantis_armor/NAME_PIECE.geo.json, see tools/convert_armor.py) with bones where the player's head, body, arms and
 * legs are; each bone follows the matching body part of the player.
 */
public final class ArmorModelLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    private final PlayerModel pose;                                     // a player model of our own, posed from the state when drawn
    private final Map<ModelPart, Piece> pieces = new IdentityHashMap<>();

    public ArmorModelLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent, EntityRendererProvider.Context ctx) {
        super(parent);
        this.pose = new PlayerModel(ctx.bakeLayer(ModelLayers.PLAYER), false);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        if (state.isInvisible) return;
        draw(state.headEquipment, poseStack, collector, light, state);
        draw(state.chestEquipment, poseStack, collector, light, state);
        draw(state.legsEquipment, poseStack, collector, light, state);
        draw(state.feetEquipment, poseStack, collector, light, state);
        if (wearsBackpack(state.id)) draw("backpack_worn", "backpack", false, poseStack, collector, light, state);
    }

    /** Is this player wearing a backpack? (The server lists who is: "serantis_backpacks", see Backpacks.) */
    private static boolean wearsBackpack(int entityId) {
        var st = TradeClient.state();
        var level = net.minecraft.client.Minecraft.getInstance().level;
        if (st == null || level == null || !st.has("serantis_backpacks")) return false;
        var e = level.getEntity(entityId);
        if (e == null) return false;
        String id = e.getUUID().toString();
        for (var el : st.getAsJsonArray("serantis_backpacks")) if (id.equals(el.getAsString())) return true;
        return false;
    }

    private void draw(ItemStack stack, PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state) {
        String name = InfluenceArmor.modelOf(stack);
        if (name == null) return;
        draw(name, InfluenceArmor.textureOf(name), stack.hasFoil(), poseStack, collector, light, state);
    }

    private void draw(String name, String texture, boolean foil, PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state) {
        var geo = GeoModels.model("serantis_armor", name);
        if (geo == null) return;
        if (pieces.size() > 64) pieces.clear();                         // models were reloaded
        Piece piece = pieces.computeIfAbsent(geo.root(), r -> new Piece(r, pose));
        Identifier tex = Identifier.parse("serantis_battle:textures/entity/armor/" + texture + ".png");
        collector.submitModel(piece, state, poseStack, foil ? RenderTypes.armorCutoutNoCullGlint(tex) : RenderTypes.armorCutoutNoCull(tex),
                light, OverlayTexture.NO_OVERLAY, 0);
    }

    /** One armour piece: its bones copy the pose of the player's body parts. */
    static final class Piece extends Model<AvatarRenderState> {
        private final PlayerModel pose;
        private final List<ModelPart[]> follow = new ArrayList<>();     // {bone, the player's part it follows}

        Piece(ModelPart root, PlayerModel pose) {
            super(root, RenderTypes::armorCutoutNoCull);
            this.pose = pose;
            var find = root.createPartLookup();
            for (String n : new String[]{"head", "body", "rightArm", "leftArm", "rightLeg", "leftLeg"}) {
                ModelPart bone = find.apply(n);
                if (bone == null) continue;
                // pair by where the bone is (the models' left/right can be mirrored): x < 0 is the player's right side
                ModelPart part = switch (n) {
                    case "head" -> pose.head;
                    case "body" -> pose.body;
                    case "rightArm", "leftArm" -> bone.x < 0 ? pose.rightArm : pose.leftArm;
                    default -> bone.x < 0 ? pose.rightLeg : pose.leftLeg;
                };
                follow.add(new ModelPart[]{bone, part});
            }
        }

        @Override
        public void setupAnim(AvatarRenderState state) {
            pose.setupAnim(state);
            for (ModelPart[] f : follow) f[0].loadPose(f[1].storePose());
        }
    }
}
