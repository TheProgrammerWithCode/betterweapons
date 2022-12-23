package net.stevious.betterweapons.entity.custom;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.impl.lib.sat4j.core.Vec;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;
import net.minecraft.entity.vehicle.BoatEntity;

import static net.stevious.betterweapons.BetterWeaponsClient.*;

public class f15Entity extends PathAwareEntity implements GeoAnimatable {
    private float yawRate = 0.0f;
    private float pitchRate = 0.0f;
    private float nextYaw = 0.0f;
    private float nextPitch = 0.0f;
    private int throttle = 0;
    private Vec3d velocity = new Vec3d(0.0f, 0.0f, 0.0f);
    private static final float defaultGravity = -0.03999999910593033f;
    private double downForce;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    public f15Entity(EntityType<? extends PathAwareEntity> entityType, World world) {
        super(entityType, world);
    }

    public static DefaultAttributeContainer.Builder setAttributes() {
        return PathAwareEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 100.0f)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 10.0f)
                .add(EntityAttributes.GENERIC_ATTACK_SPEED, 1.0f)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 1.0f);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllerRegistrar) {

    }

    @Override
    public double getTick(Object o) {
        return 0;
    }

    public boolean isKey(ItemStack itemstack) {
        return false;
    }

    public ActionResult keyItem(PlayerEntity player, ItemStack itemstack) {
        return ActionResult.success(this.world.isClient);
    }

    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (player.shouldCancelInteraction()) {
            return ActionResult.PASS;
        }
        if (!this.hasPassengers()) {
            ItemStack itemStack = player.getStackInHand(hand);
            if (!itemStack.isEmpty()) {
                if (this.isKey(itemStack)) {
                    return this.keyItem(player, itemStack);
                }
            }

            player.startRiding(this);
            return super.interactMob(player, hand);
        } else {
            return super.interactMob(player, hand);
        }
    }

    public void updatePassengerPosition(Entity passenger) {
        Vec3d vec3d = new Vec3d(0.0d, 0.8d, 4.0d).rotateY(-this.getYaw() * 0.017453292F);
        passenger.setPosition(this.getX()+vec3d.x, this.getY()+vec3d.y, this.getZ()+vec3d.z);
    }

    public Vec3d updatePassengerForDismount(LivingEntity entity) {
        Vec3d vec3d = new Vec3d(3.0d, 0.0d, 4.0d).rotateY(-this.getYaw() * 0.017453292F);
        return new Vec3d(this.getX()+vec3d.x, this.getY()+vec3d.y, this.getZ()+vec3d.z);
    }

    public void tick() {

        super.tick();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (this.getFirstPassenger() instanceof PlayerEntity) {
                if (planePitchUp.isPressed()) {
                    if (!this.onGround) {
                        this.pitchRate = MathHelper.clamp(this.pitchRate + 1, -1.0f, 1.0f);
                    }
                }
                if (planePitchDown.isPressed()) {
                    if (!this.onGround) {
                        this.pitchRate = MathHelper.clamp(this.pitchRate - 1, -1.0f, 1.0f);
                    }
                }
                if (planeSteerLeft.isPressed()) {
                    this.yawRate = MathHelper.clamp(this.yawRate - 1.0f, -1.0f, 1.0f);
                }
                if (planeSteerRight.isPressed()) {
                    this.yawRate = MathHelper.clamp(this.yawRate + 1.0f, -1.0f, 1.0f);
                }
                while (planeThrottleUp.wasPressed()) {
                    throttle = MathHelper.clamp(throttle + 1, -3, 10);
                    client.player.sendMessage(Text.literal("Throttle level: " + throttle), true);
                }
                while (planeThrottleDown.wasPressed()) {
                    throttle = MathHelper.clamp(throttle - 1, -3, 10);
                    client.player.sendMessage(Text.literal("Throttle level: " + throttle), true);
                }
                client.player.sendMessage(Text.literal("Debug Readout: " + throttle), true);
            }
        });

        //determine forward force from facing direction
        Vec3d fdForce = new Vec3d(0.0f, 0.0f, throttle*-0.01f)
                .rotateX(this.getPitch()*0.0174533F)
                .rotateY(this.getYaw()*-0.0174533F-3.1415926F); //Forward force
        velocity = velocity.add(fdForce);
        velocity = velocity.multiply(0.9f);

        //generate fall velocity based on throttle position (cheesy aerodynamics simulation)
        downForce = (1-MathHelper.abs(throttle)/10f)*defaultGravity;
        velocity = velocity.add(new Vec3d(0.0d, downForce, 0.0d));

        //move
        this.setVelocity(velocity);
        this.move(MovementType.SELF, this.getVelocity());

        this.nextYaw = this.getYaw() + this.yawRate;
        this.nextPitch = this.getPitch() + this.pitchRate;
        if (this.hasPassengers()) {
            this.setHeadYaw(this.nextYaw);
            this.setBodyYaw(this.nextYaw);
            this.setYaw(this.nextYaw);
            this.prevPitch = this.getPitch();
            this.setPitch(this.nextPitch);
        }
        this.yawRate = 0.0f;
        this.pitchRate = 0.0f;
        this.updatePositionAndAngles(this.getX(), this.getY(), this.getZ(), this.getYaw(), this.getPitch());
    }

}