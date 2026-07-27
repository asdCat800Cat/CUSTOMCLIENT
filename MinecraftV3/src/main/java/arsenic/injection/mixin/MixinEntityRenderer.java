package arsenic.injection.mixin;

import arsenic.event.impl.EventRenderWorldLast;
import arsenic.main.Arsenic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EntityRenderer.class, priority = 995)
public abstract class MixinEntityRenderer implements IResourceManagerReloadListener {

    @Shadow
    private Entity pointedEntity;

    @Shadow
    private Minecraft mc;

    @Inject(method = "renderWorldPass", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/EntityRenderer;renderHand:Z", shift = At.Shift.BEFORE))
    private void renderWorldPass(int pass, float partialTicks, long finishTimeNano, CallbackInfo callbackInfo) {
        Arsenic.getArsenic().getEventManager().getBus().post(new EventRenderWorldLast(mc.renderGlobal, partialTicks));
    }
}
