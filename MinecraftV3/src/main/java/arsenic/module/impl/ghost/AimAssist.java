package arsenic.module.impl.ghost;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventSilentRotation;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.impl.client.AntiBot;
import arsenic.module.impl.client.TargetManager;
import arsenic.module.property.PropertyInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.utils.rotations.RotationUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.ThreadLocalRandom;

@ModuleInfo(name = "AimAssist", category = ModuleCategory.GHOST)
public class AimAssist extends Module {

    // Aim Mode Settings
    public final EnumProperty<AimMode> mode = new EnumProperty<>("Mode", AimMode.Silent);
    
    @PropertyInfo(reliesOn = "Mode", value = "Silent")
    public final BooleanProperty movementFix = new BooleanProperty("MovementFix", true);

    // Speed Settings
    public final RangeProperty speed = new RangeProperty("Speed 1", new RangeValue(5, 100, 45, 1));
    public final RangeProperty speedCompliment = new RangeProperty("Speed 2", new RangeValue(2, 97, 15, 1));

    // Target Settings
    public final DoubleProperty fov = new DoubleProperty("FOV", new DoubleValue(15, 360, 90, 1));
    public final DoubleProperty distance = new DoubleProperty("Distance", new DoubleValue(1, 10, 4.5, 0.5));

    // Behavior Settings
    public final BooleanProperty clickOnly = new BooleanProperty("Click Only", true);
    public final BooleanProperty breakBlocks = new BooleanProperty("Break Blocks", true);
    public final BooleanProperty aimInvis = new BooleanProperty("Aim Invis", false);
    public final BooleanProperty ignoreFriends = new BooleanProperty("Ignore Friends", true);
    public final BooleanProperty blatantMode = new BooleanProperty("Blatant Mode", false);
    public final BooleanProperty pitchAssist = new BooleanProperty("Pitch Assist", true);
    public final BooleanProperty fovBased = new BooleanProperty("FOV Based", true);

    // Friends list
    private static final ArrayList<Entity> friends = new ArrayList<>();

    @RequiresPlayer
    @EventLink
    public Listener<EventSilentRotation> eventSilentRotationListener = event -> {
        if (mc.currentScreen != null) return;
        if (clickOnly.getValue() && !mc.gameSettings.keyBindAttack.isKeyDown()) return;
        if (breakBlocks.getValue() && mc.objectMouseOver != null && 
            mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) return;

        Entity target = getEnemy();
        if (target == null) return;

        double fovDiff = RotationUtils.fovFromEntity(target);
        
        if (mode.getValue() == AimMode.Silent) {
            // Silent mode with smooth rotation
            double complimentSpeed = fovDiff * (ThreadLocalRandom.current().nextDouble(
                    speedCompliment.getValue().getMin(), 
                    speedCompliment.getValue().getMax()) / 100.0);
            
            double totalSpeed = complimentSpeed + ThreadLocalRandom.current().nextDouble(
                    speed.getValue().getMin() - 4.723847, 
                    speed.getValue().getMax());
            
            float rotationVal = (float)(-(complimentSpeed + fovDiff / (101.0D - ThreadLocalRandom.current().nextDouble(
                    speed.getValue().getMin() - 4.723847, 
                    speed.getValue().getMax()))));

            event.setDoMovementFix(movementFix.getValue());
            event.setJumpFix(movementFix.getValue());
            event.setSpeed(Math.abs(rotationVal) / 100f);

            float[] targetRots = RotationUtils.getRotationsToEntity((EntityLivingBase) target);
            if (targetRots != null) {
                event.setYaw(targetRots[0]);
                event.setPitch(pitchAssist.getValue() ? targetRots[1] : mc.thePlayer.rotationPitch);
            }
        } else {
            // Normal mode with capped rotations
            float[] targetRots = RotationUtils.getRotationsToEntity((EntityLivingBase) target);
            if (targetRots == null) return;

            double rotSpeed = speed.getValue().getRandomInRange() * 
                    (fovBased.getValue() ? (Math.abs(fovDiff) * 2 / 180) : 1);

            float[] rots = RotationUtils.getPatchedAndCappedRots(
                    new float[]{mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch},
                    new float[]{targetRots[0], targetRots[1]},
                    (float) rotSpeed
            );
            mc.thePlayer.rotationYaw = rots[0];
            mc.thePlayer.rotationPitch = rots[1];
        }
    };

    @Override
    protected void onEnable() {
        friends.clear();
    }

    /**
     * Gets the best enemy to aim at based on configured settings
     */
    public Entity getEnemy() {
        int fovValue = (int) fov.getValue().getInput();
        Iterator<EntityPlayer> iterator = mc.theWorld.playerEntities.iterator();

        while (iterator.hasNext()) {
            EntityPlayer player = iterator.next();

            // Filter conditions
            if (ignoreFriends.getValue() && isAFriend(player)) continue;
            if (player == mc.thePlayer) continue;
            if (player.deathTime != 0) continue;
            if (!aimInvis.getValue() && player.isInvisible()) continue;
            if (mc.thePlayer.getDistanceToEntity(player) > distance.getValue().getInput()) continue;
            if (AntiBot.isBot(player)) continue;
            if (!blatantMode.getValue() && !RotationUtils.fovToEntity(player) >= -fovValue 
                    && !RotationUtils.fovToEntity(player) <= fovValue) continue;

            return player;
        }
        return null;
    }

    /**
     * Checks if an entity is in the friends list
     */
    public static boolean isAFriend(Entity entity) {
        if (entity == mc.thePlayer) return true;

        for (Entity friend : friends) {
            if (friend.equals(entity)) return true;
        }

        try {
            EntityPlayer player = (EntityPlayer) entity;
            if (mc.thePlayer.isOnSameTeam((EntityLivingBase) entity)) return true;
            
            String playerTag = mc.thePlayer.getDisplayName().getUnformattedText();
            String entityTag = player.getDisplayName().getUnformattedText();
            
            if (playerTag.length() > 1 && entityTag.length() > 1) {
                if (playerTag.substring(0, 2).equals(entityTag.substring(0, 2))) return true;
            }
        } catch (Exception ignored) {
        }

        return false;
    }

    /**
     * Adds a friend by entity reference
     */
    public static void addFriend(Entity entity) {
        if (!isAFriend(entity)) {
            friends.add(entity);
        }
    }

    /**
     * Adds a friend by player name
     */
    public static boolean addFriend(String name) {
        boolean found = false;
        for (Entity entity : mc.theWorld.getLoadedEntityList()) {
            if ((entity instanceof EntityPlayer) && 
                (entity.getName().equalsIgnoreCase(name) || 
                 ((EntityPlayer)entity).getDisplayName().getUnformattedText().equalsIgnoreCase(name))) {
                if (!isAFriend(entity)) {
                    addFriend(entity);
                    found = true;
                }
            }
        }
        return found;
    }

    /**
     * Removes a friend by player name
     */
    public static boolean removeFriend(String name) {
        boolean removed = false;
        for (Entity friend : new ArrayList<>(friends)) {
            if (friend instanceof EntityPlayer) {
                EntityPlayer player = (EntityPlayer) friend;
                if (player.getName().equalsIgnoreCase(name) || 
                    player.getDisplayName().getUnformattedText().equalsIgnoreCase(name)) {
                    removed = removeFriend(friend);
                }
            }
        }
        return removed;
    }

    /**
     * Removes a friend by entity reference
     */
    public static boolean removeFriend(Entity entity) {
        try {
            return friends.remove(entity);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Gets the friends list
     */
    public static ArrayList<Entity> getFriends() {
        return new ArrayList<>(friends);
    }

    /**
     * Aim modes
     */
    public enum AimMode {
        Silent,
        Normal
    }
}
